# 3. Writing Scripts

## 3.1. Script Bytecode

Paper Mario uses a custom scripting language to implement most of its high-level game logic. These scripts are compiled into bytecode that is read by an interpreter at runtime. They often call functions to execute particular tasks like `PlaySound()` or `RemoveActor()`. This is quite fortunate for the modding community, as it allows for easy higher-level modifications. It is easy to add, for example, new enemy attacks.

Internally, each script has a **script context**, a data structure that manages the state of the script, stores its variable values, etc. A new script context is created every time a new script is run and the context is deleted when the script is finished executing.

A single script may use up to sixteen labels. Loops and switch blocks may each be nested eight levels deep. These are engine limits rather than arbitrary limits imposed by the text format.

## 3.2. Inline Script Expressions

The value of script variables can be assigned with mathematical expressions like this:

```star-rod
Set   *Var[0] = *Var[2] + *Var[3] - 10`
```

These are automatically compiled into ordinary bytecode commands and optimized for space. Use `Set` and `SetF` to distinguish between values which should be handled as integers or floats. Parentheses, arithmetic, bitwise operators, and the conversion helpers accepted by the inline parser may be combined in the same expression.
