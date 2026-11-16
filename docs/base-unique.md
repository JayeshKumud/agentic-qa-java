# Base uniques: `TestSession.put(Keys.BASE_UNIQUE, ...)` instead of `setBaseUniqueMap`

The existing framework keeps the base uniques of a scenario in a static `ThreadLocal` map in `TestSession`. This
framework keeps them in the scenario's own `TestSession`, under a typed key. This page shows both side by side and
explains why the second is safer, shorter to extend and easier to debug.

## The two versions

### Existing framework: static ThreadLocal

```java
// TestSession
private static final ThreadLocal<HashMap<String, Unique>> baseUnique = ThreadLocal.withInitial(HashMap::new);

public static void setBaseUniqueMap(String baseUnique, Unique unique) {
    if (unique == null) {
        throw new AssertionError("unique provided is null");
    }
    TestSession.baseUnique.get().put(baseUnique, unique);
}

public static HashMap<String, Unique> getBaseUniqueMap() {
    HashMap<String, Unique> uniqueMap = TestSession.baseUnique.get();
    if (uniqueMap.isEmpty()) {
        logger.info("No dynamic unique found");
    }
    return uniqueMap;
}

public static void clear() {           // must run after every scenario
    baseUnique.remove();
}
```

### This framework: a key and the scenario's session

```java
// Keys.java: the only declaration
public static final DataKey<Map<String, Unique>> BASE_UNIQUE =
        DataKey.mapOf("baseUnique", String.class, Unique.class);

// store, from any step or helper (UniqueSteps)
TestSession.current().put(Keys.BASE_UNIQUE, "U1", unique);

// read, from any step or helper (BackUniqueTransit)
Map<String, Unique> baseUniques = TestSession.current().map(Keys.BASE_UNIQUE);
```

Nothing else: no field, no setter, no getter and no `clear()` in `TestSession`. A step class that has the session
injected writes `session.put(BASE_UNIQUE, name, unique)`, without `current()`.

## How they compare

| | `setBaseUniqueMap` (static ThreadLocal) | `TestSession.put(Keys.BASE_UNIQUE, ...)` |
|---|---|---|
| Where the data lives | one map per **thread** | one map per **scenario** (in its `TestSession`) |
| A pooled thread runs the next scenario | it **inherits** the old uniques unless `clear()` ran | it gets a new session with an **empty** map |
| Clean-up | `clear()` after every scenario, one line per variable | none: the map ends with the session |
| A retried scenario | sees the failed attempt's uniques if `clear()` was skipped | starts empty, like a fresh scenario |
| Parallel scenarios | separate, as long as each stays on its thread **and** `clear()` always runs | separate by construction (tested) |
| Called outside a scenario, or on another thread | **silently** works on some other, usually empty, map | **fails clearly**: `No scenario is running on this thread` |
| What the getter returns | the **live** `HashMap`: any caller can `put`, `remove` or `clear()` it | a **read-only copy**: only `put` changes the data |
| Null unique | `AssertionError("unique provided is null")` | `IllegalArgumentException("Cannot put a null key or value in 'baseUnique' ...")` |
| Null base name | accepted (`HashMap` allows a `null` key) | rejected, same message |
| Order of entries | none (`HashMap`) | the order they were stored (`LinkedHashMap`) |
| Return type | `HashMap<String, Unique>`: callers depend on the class | `Map<String, Unique>` |
| Adding a second kind of data | a new `ThreadLocal`, setter, getter **and** a line in `clear()` in `TestSession` | **one line** in `Keys.java` |
| Where shared data is declared | spread over `TestSession` (main code) | all in `Keys.java`, with who writes and reads each key |
| Memory | a thread that is never cleared keeps the uniques alive for the whole run | freed with the session |

## Why each difference matters

### 1. Data per scenario, not per thread

Test threads are pooled. `TestNG-PoolService-1` runs scenario A, then scenario B, then C. A `ThreadLocal` belongs
to the thread, so it outlives scenario A. Everything depends on `clear()` running at exactly the right moment.

`TestSession` is created by PicoContainer for one scenario and discarded after it. A map stored in it cannot reach
the next scenario, whatever the next scenario's thread is.

### 2. No `clear()` to forget

`clear()` only works if every exit path calls it: a hook that throws before reaching it, a new hook written
without it, a retry that runs differently, or a new `ThreadLocal` someone adds to `TestSession` but not to
`clear()`. Each of these leaks data into a later scenario, and the failure shows up in that later scenario, far
from the cause.

Here the clean-up is the end of the session, which always happens: `Hooks.after` closes it, pass or fail, and
PicoContainer drops it. There is nothing to forget, for this key or any key added later.

### 3. Wrong use fails loudly instead of returning wrong data

With the ThreadLocal, `getBaseUniqueMap()` always returns *a* map, whichever thread asks:

```java
CompletableFuture.supplyAsync(() -> TestSession.getBaseUniqueMap().get("U1"))   // null: another thread's map
```

The code continues with `null` or the raw base name, and the scenario fails later with a confusing message, or
passes while sending the wrong id.

`TestSession.current()` fails on the spot with `No scenario is running on this thread`, so the mistake is found
where it is made.

### 4. Nobody can change the data by accident

`getBaseUniqueMap()` hands out the stored `HashMap` itself, so `getBaseUniqueMap().clear()` or `.remove("U1")`
anywhere changes what every later step sees. `map(Keys.BASE_UNIQUE)` returns a read-only copy: the only way to
change the data is `put`, which is easy to search for.

### 5. One line per new kind of data

The ThreadLocal pattern costs four pieces of code in `TestSession` for every value the scenarios share: the field,
the setter, the getter and the line in `clear()`, plus the null check copied into each setter. With keys, a new
value is one constant:

```java
public static final DataKey<Map<String, Order>> ORDERS = DataKey.mapOf("orders", String.class, Order.class);
```

and `put`, `map`, the null check, the copy and the clean-up already work for it. `TestSession` does not change.

### 6. One place lists what scenarios share

`Keys.java` declares every shared value once, with its type and a comment saying which step writes it and which
reads it. With static ThreadLocals the shared data is spread over `TestSession` (and often other classes), and a
second class can create its own map for the same idea under another name.

## The same lookup, both ways

`BackUniqueTransit` swaps base names for registration ids. In the existing framework:

```java
for (String unique : uniqueIds.split(",")) {
    if (TestSession.getBaseUniqueMap().get(unique) != null) {           // looked up before trimming
        unique = TestSession.getBaseUniqueMap().get(unique).getUniqRegId();
    }
    uniqueRegIds.add(unique.trim());
}
```

In this framework:

```java
Map<String, Unique> baseUniques = TestSession.current().map(Keys.BASE_UNIQUE);   // read once
for (String unique : uniqueIds.split(",")) {
    String name = unique.trim();                                                  // trim first
    Unique stored = baseUniques.get(name);
    uniqueRegIds.add(stored != null ? stored.getUniqRegId() : name);
}
```

Apart from where the data lives, this fixes a bug in the original: for `"U1, U2"` the second value is `" U2"` with
a leading space, so the lookup misses `U2` and the raw base name is sent instead of its registration id. It also
reads the map once instead of twice per value, and logs "No dynamic unique found" once instead of on every lookup.

## Proof

These tests in `src/test/java/com/agenticqa/helpers/BackUniqueTransitTest.java` run without an app or a browser:

| Test | Shows |
|---|---|
| `parallelScenariosEachSeeOnlyTheirOwnUniques` | 16 overlapping scenarios on 8 threads, each storing a different `U1`, each get only their own |
| `aReusedThreadDoesNotSeeThePreviousScenariosUniques` | the next scenario on the same thread starts empty, with no `clear()` |
| `mapHelpersKeepOrderReplaceRejectNullAndReturnACopy` | order kept, same name replaced, null rejected, read-only result |
| `replacesStoredBaseNamesTrimmedAndKeepsOtherValues` | `"U1, U2 ,REG-77,"` gives `[REG-1001, REG-1002, REG-77]` |
| `needsARunningScenario` | used outside a scenario, it fails with a clear message |

And `features/api/back_unique_transit.feature` runs the same flow as two parallel scenarios.

## What it costs

- **A slightly longer call:** `TestSession.current().put(Keys.BASE_UNIQUE, name, unique)` instead of
  `TestSession.setBaseUniqueMap(name, unique)`. In a step class with the injected session it is
  `session.put(BASE_UNIQUE, name, unique)`.
- **Only inside a running scenario, on its thread.** That is the point of the change: the old version also
  "worked" elsewhere, but returned the wrong map.
- **A different exception for a null unique:** `IllegalArgumentException` instead of `AssertionError`. Both fail the
  step and the scenario the same way.

## Porting existing code

| Existing framework | This framework |
|---|---|
| `TestSession.setBaseUniqueMap(name, unique)` | `TestSession.current().put(Keys.BASE_UNIQUE, name, unique)` |
| `TestSession.getBaseUniqueMap()` | `TestSession.current().map(Keys.BASE_UNIQUE)` |
| `TestSession.getBaseUniqueMap().get(name)` | `TestSession.current().map(Keys.BASE_UNIQUE).get(name)` |
| `TestSession.clear()` | delete the call |
| `HashMap<String, Unique>` variables | `Map<String, Unique>` |
| the `baseUnique` ThreadLocal, setter, getter in `TestSession` | delete them |

Other static ThreadLocals from the existing framework (`unitKL`, `createdInstMan`, ...) move the same way, to a
`DataKey.listOf` or `DataKey.mapOf` key in `Keys.java`. See [keys.md](keys.md).
