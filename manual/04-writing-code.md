# 4. Writing Code

## 4.1. Assembly

The N64 uses the NEC VR4300 CPU, closely related to the MIPS R4300i. As such, the compiled machine code in Paper Mario can be disassembled into MIPS assembly language. Star Rod uses a custom MIPS assembler/disassembler to convert between machine code on the ROM and a (more) user-friendly assembly language.

As functions are dumped from the ROM, branches and jump tables are recognized and appropriate labels are created for their destinations. You can use labels as branch targets in your own code. You can also reference pointers (e.g. `$Script_Example`), script variables (e.g. `*StoryProgress`), and special functions (e.g. `{Func:GetVariable}`).

***Note:*** Register 30 may be referenced as either S8 or FP. Both are accepted.

## 4.2. Pseudo-instructions

During the dump, certain sequences of instructions are replaced by pseudo-instructions (PIs) to help find pointers and data structures that would otherwise be 'hidden' within functions. In this way, relocated data structures can have their pointers properly updated. You are encouraged to use these pseudo-instructions in your own code to improve readability and reduce errors.

### 4.2.1. Convenience

| Instruction | Description |
| --- | --- |
| `CLEAR X` | Set X = 0. Equivalent to `DADDU X, R0, R0` |
| `COPY X, Y` | Set X = Y. Equivalent to `DADDU X, Y, R0` |
| `SUBI X, Y, 1234` | Subtracts an immediate value. |
| `SUBIU X, Y, 1234` | Subtracts an immediate value (unsigned). |
| `LA X, 12345678` | Load an address or constant word to a register (ADD variant). `LIA` is also accepted. |
| `LI X, 12345678` | Load a constant word to a register (OR variant). `LIO` is also accepted. |
| `LIF FX, 3.25` | Load a constant float to COP1 register. |

### 4.2.2. Load/Store Address

| Instruction | Description |
| --- | --- |
| `LAB X, ADDR` | Load a byte from ADDR to register. |
| `LABU X, ADDR` | Load an unsigned byte from ADDR to register. |
| `SAB X, ADDR` | Store a byte from register to ADDR. |
| `LAH X, ADDR` | Load a half-word from ADDR to register. |
| `LAHU X, ADDR` | Load an unsigned half-word from ADDR to register. |
| `SAH X, ADDR` | Store a half-word from register to ADDR. |
| `LAW X, ADDR` | Load a word from ADDR to register. |
| `SAW X, ADDR` | Store a word from register to ADDR. |
| `LAF FX, ADDR` | Load a float from ADDR to COP1 register. |
| `SAF FX, ADDR` | Store a float from COP1 register to ADDR. |
| `LAD FX, ADDR` | Load a double float from ADDR to COP1 register. |

### 4.2.3. Load/Store Table

| Instruction | Description |
| --- | --- |
| `LTB X, Y (ADDR)` | Load the Yth byte from ADDR to X. |
| `LTBU X, Y (ADDR)` | Load the Yth unsigned byte from ADDR to X. |
| `LTH X, Y (ADDR)` | Load the Yth half-word from ADDR to X. |
| `LTHU X, Y (ADDR)` | Load the Yth unsigned half-word from ADDR to X. |
| `LTW X, Y (ADDR)` | Load the Yth word from ADDR to X. |
| `LTF FX, Y (ADDR)` | Load the Yth float from ADDR to COP1 register FX. |
| `STB X, Y (ADDR)` | Store X at the Yth byte from ADDR. |
| `STH X, Y (ADDR)` | Store X at the Yth half-word from ADDR. |
| `STW X, Y (ADDR)` | Store X at the Yth word from ADDR. |
| `STF FX, Y (ADDR)` | Store X at the Yth float from ADDR. |

### 4.2.4. Stack Operations

Standard MIPS calling conventions require functions to preserve the values of certain registers when they are called. These values are saved to the stack at the beginning of the function and restored at the end. To minimize the opportunity for errors, Star Rod introduces several simple pseudo-instructions for these stack operations: `PUSH`, `POP`, and `JPOP` (identical to `POP + JR RA + NOP`). You can use these with multiple registers, but be sure to list the same registers in the same order for both.

| Instruction | Description |
| --- | --- |
| `PUSH X, Y, ...` | Reserve a default 0x10-byte local area, then save a list of registers beginning at `SP[10]`. Updates the stack pointer and pads the frame to 8-byte alignment as needed. |
| `POP X, Y, ...` | Restores registers from the stack. Order is important! Use the same list as the corresponding push. Updates the stack pointer. |
| `JPOP X, Y, ...` | Identical to `POP` followed by `JR RA`. Stack addition is done in the delay slot of the `JR`, so this does not need to be followed by a `NOP`. |

### 4.2.5. Branch Conditions

| Instruction | Description |
| --- | --- |
| `BLT X, Y, label` | Branch to `label` if X < Y. |
| `BGT X, Y, label` | Branch to `label` if X > Y. |
| `BLE X, Y, label` | Branch to `label` if X <= Y. |
| `BGE X, Y, label` | Branch to `label` if X >= Y. |
| `BLTL/BGTL/BLEL/BGEL X, Y, label` | Branch likely variants. |

### 4.2.6. Special

| Instruction | Description |
| --- | --- |
| `RESERVED` | Put this in a delay slot following a PI and a jump to force the final instruction of the compiled PI to occupy the delay slot. |

## 4.3. Loops

Loops can be created using the `LOOP` and `ENDLOOP` pseudo-instruction. Instructions between them become the body of the loop. Labels and branching logic are created automatically and only a single register is required for the loop index value. You may also exit the loop at any time from within its body with the `BREAKLOOP` pseudo-instruction.

### 4.3.1. For Loops

```star-rod
LOOP index = start,step,end
    ...
ENDLOOP
```

This loop will initialize the value of a CPU register (**index**) to **start** and increment it by **step** at the end of each iteration until it equals or exceeds **end**. The condition is also checked before the first iteration. The other three values can be CPU registers or immediate integers whose absolute value is at most `7FFF`. Omitting **step** defaults to +1. If a register is used for **step** or **end**, you should avoid modifying its value in the loop body.

The generated comparison is always `index < end`, so this helper only supports ascending loops with a positive step. Write the branches yourself when you need a decrementing loop.

For loop examples:

| Instruction | Result |
| --- | --- |
| `LOOP A0 = 0,5` | Executes body with A0 = 0,1,2,3,4 |
| `LOOP T1 = 1,2,10` | Executes body with T1 = 1,3,5,7,9 |
| `LOOP SP = SP,4,T4` | Executes body, incrementing SP by 4 until it equals or exceeds T4. |

### 4.3.2. While Loops

```star-rod
LOOP index < value
    ...
ENDLOOP
```

This loop will execute the body so long as the condition is true. The condition is checked prior to each iteration. **index** must be a CPU register and **value** may be a CPU register or an immediate value whose absolute value is at most `7FFF`.

The supported comparisons are `==`, `!=`, `>`, `>=`, `<`, and `<=`.

While loop examples:

```star-rod
LOOP A0 < 10`
LOOP S4 >= 0
LOOP V0 != V1
LOOP T0 <= 20`
```

## 4.4. Register Naming

Registers can be assigned names with `#DEF` and reverted to their default names with `#UNDEF`. For example:

```mipsasm
#DEF   A0, *Counter
CLEAR  *Counter
ADDIU  *Counter, *Counter, 1
ADDIU  *Counter, *Counter, 1
#UNDEF A0
COPY   A1, A0
```

In this example, A0 may only be referred to as `*Counter` until the name is cleared by `#UNDEF`.

You can clear more than one register name in a single line,e.g., `#UNDEF A0, A1, A2`. Use `#UNDEF ALL` to clear every register name currently in use.

## 4.5. Examples

### 4.5.1. Check if a badge is equipped

```mipsasm
% in:  A0 = badge ID to check for
% out: V0 = 1 if badge equipped, 0 if not
#DEF    A0, *BadgeID
PUSH    S0, S1             % Push saved registers to stack
LIO     S0, 8010F498       % Initialize loop start
ADDIU   S1, S0, 80         % 0x40 badge slots
LOOP    S0 = S0:2:S1
    LHU     V1, 0 (S0)
    BEQL    V1, *BadgeID, .Done
    ADDIU   V0, R0, 1      % Return TRUE
ENDLOOP
CLEAR   V0                 % Return FALSE
.Done
JPOP    S0, S1             % Pop saved registers from stack
```

## 4.6. Writing Script-Callable Functions

Functions which scripts can `Call` must have the following C signature:

```c
ApiStatus Function(Evt* script, s32 isInitialCall)
```


**Arguments:**

| Register | Value |
| --- | --- |
| A0 | Pointer to the caller's `Evt` structure. |
| A1 | `isInitialCall` boolean, true on the first call, false on subsequent ones |

To access script arguments to the function, follow the `Evt` pointer on `A0` to `script->ptrReadPos`, which is offset `0xC` in the current `Evt` structure. The argument list will begin at the position found there.

You may store values in `varTable` or use the four-word `functionTemp` array at `script + 0x70` to store temporary values between repeated calls.

**Valid return values:**

| Value | Name / behavior |
| ---: | --- |
| 0 | `ApiStatus_BLOCK` - call the function again on the next frame. |
| 1 | `ApiStatus_YIELD` - finish the call, but don't let script advance until next frame. |
| 2 | `ApiStatus_DONE` - finish the call, and let script continue executing. This is the usual return value for a completed API call. |
