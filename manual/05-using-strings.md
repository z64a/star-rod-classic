# 5. Using Strings

## 5.1. String Theory

Strings (or Messages) in Paper Mario use a special bytecode format which Star Rod translates into a readable markup language. Ordinary text is written normally, while line breaks, button icons, message-box styles, colors, pauses, and other commands are written as tags in square brackets. When you build your mod, Star Rod converts the markup back into the game's native message format.

Every vanilla message has a 16-bit section ID and a 16-bit message ID. For example, `001C0002` is message 2 in section 1C. The dumped messages in `strings/src/` are an invaluable source of complete, working examples.

Tags are case-insensitive. This chapter uses the newer decomp-style syntax:

```star-rod
[Style Right]
[Speed delay=6 chars=3]
[Size 20,20]
```

An older colon syntax is also accepted. Numbers in colon-style tags are hexadecimal, while numbers in the newer syntax are decimal unless they begin with `0x`:

```star-rod
[STYLE:RIGHT]
[SIZE:14:14]
```

Newlines in the source file are ignored. Use `[BR]` when you actually want a line break in the message.

## 5.2. Star Rod String Markup Guide

### 5.2.1. Special Characters

| Type | Characters |
| --- | --- |
| Colored button icons | `[A] [B] [L] [R] [Z] [C-UP] [C-DOWN] [C-LEFT] [C-RIGHT] [START]` |
| Uncolored button glyphs | `[~A] [~B] [~L] [~R] [~Z] [~C-UP] [~C-DOWN] [~C-LEFT] [~C-RIGHT] [~START]` |
| Solid arrows | `[UP] [DOWN] [LEFT] [RIGHT]` |
| Miscellaneous shapes | `[NOTE] [HEART] [STAR] [CIRCLE] [CROSS]` |

The characters `%`, `[`, `]`, `{`, and `}` have special meaning in source files. Write them as `\%`, `\[`, `\]`, `\{`, and `\}` when you want them to appear as text.

### 5.2.2. Control Tags

| Tag | Description |
| --- | --- |
| `[BR]` | Starts a new line. |
| `[Wait]` | Stops and waits for the player to press A. |
| `[Pause 20]` | Pauses printing for a fixed amount of time. |
| `[Next]` | Advances to the next page of the current message. |
| `[Style Right]` | Selects the message-box style. This is normally the first tag in a message. |
| `[Yield]` | Ends this part of the message without closing the box. Use it when the script will continue the same conversation. |
| `[End]` | Terminates the string. Every complete string must end with this tag. |

### 5.2.3. Message Box Styles

| Style | Description |
| --- | --- |
| `Right` | Standard NPC speech bubble connected to the speaker from the right side. |
| `Left` | Standard NPC speech bubble connected to the speaker from the left side. |
| `Center` | Standard NPC speech bubble connected to the speaker from the center. |
| `Tattle` | Adaptive speech bubble used for Goombario's map tattles. |
| `Choice` | Choice box with a custom position and size: `[Style Choice pos=96,112 size=128,62]`. |
| `Inspect` | Gray box with a scrolling background, used when inspecting things. |
| `Sign` | Wooden sign message box. |
| `Lamppost` | Metallic sign used for the Toad Town lamppost: `[Style Lamppost height=72]`. |
| `Postcard` | Displays a postcard on the lower half of the screen: `[Style Postcard index=0]`. |
| `Popup` | Small, automatically sized popup in the center of the screen. |
| `Upgrade` | Upgrade-block box with a custom position and size. It takes the same arguments as `Choice`. |
| `Narrate` | Centered announcement such as "You got an item!" or "X joined your party!" |
| `Epilogue` | Silent, centered text with no box, used for End of Chapter scenes. |

### 5.2.4. Functions

#### Text Formatting and Appearance

| Tag | Description |
| --- | --- |
| `[Font Normal]` | Selects `Normal`, `Menu`, `Title`, or `Subtitle`. The credits fonts have limited character sets. |
| `[Variant 0]` | Selects a font variant from 0 through 3. |
| `[Color 16]` | Sets the text color by palette index. |
| `[SaveColor]` / `[RestoreColor]` | Saves and restores the current color. There is only one saved color; these do not form a stack. |
| `[Spacing 8]` | Forces every character to use the given width. Zero restores normal character spacing. |
| `[Size 20,20]` | Sets the horizontal and vertical text scale. A single value sets both. The default is 16,16 (`0x10,0x10`). |
| `[SizeReset]` | Restores the default text size. |
| `[CenterX 160]` | Centers the following text around the given X coordinate. |

#### Interaction and Printing

| Tag | Description |
| --- | --- |
| `[NoSkip]` | Prevents the player from skipping the message. |
| `[InputOff]` / `[InputOn]` | Disables or enables input handling while the message prints. |
| `[DelayOff]` / `[DelayOn]` | Disables or enables the normal delay between printed characters. |
| `[Scroll 1]` | Scrolls the text by the given number of lines. |
| `[Speed delay=6 chars=3]` | Prints a group of `chars` characters after each `delay`. |
| `[Voice Bowser]` | Selects the `Normal`, `Bowser`, or `Star` printing voice. `Spirit` is accepted as an alias for `Star`. |
| `[CustomVoice soundIDs=0x141,0x142]` | Supplies a custom pair of sound IDs for the printing voice. |
| `[Volume percent=50]` | Sets the volume of the printing voice. A raw value from 0 through 255 may be used instead. |
| `[EnableCDownNext]` | Allows C-Down to advance to the next page. Used in certain menus. |
| `[RewindOn]` / `[RewindOff]` | Enables or disables the message rewind state. |

#### Printing Position

| Tag | Description |
| --- | --- |
| `[SetPos 80,32]` | Sets both the X and Y printing position. X may range from 0 through 320; Y may range from -255 through 255. |
| `[SetPosX 80]` | Sets the X printing position. |
| `[SetPosY 32]` | Sets the Y printing position. |
| `[Right 8]` | Moves the following text to the right. The offset resets on a new line. |
| `[Down 8]` / `[Up 8]` | Moves the following text vertically. |
| `[SavePos]` / `[RestorePos]` | Saves and restores the current printing position. There is only one saved position. |

#### Choices

| Tag | Description |
| --- | --- |
| `[StartChoice]` | Starts a choice list and disables the normal printing delay. |
| `[Option 0]` | Marks the beginning of an option. In a smart choice, Star Rod also places its hand cursor here. |
| `[EndChoice cancel=1]` | Finishes a smart choice. The optional `cancel` value is returned when the player presses B. |
| `[Cursor 0]` | Manually places the hand cursor for option 0. This is only needed for old-style choice markup. |
| `[SetCancel 1]` | Sets the B-button result for an old-style choice. |

The game supports choice indices 0 through 5. Prefer the `StartChoice` form shown below; it supplies the bookkeeping tags which old messages had to write by hand.

#### Images, Item Icons, and Sprites

| Tag | Description |
| --- | --- |
| `[ItemIcon itemName=Mushroom]` | Draws an item icon by item name. `itemID=0x80` may be used instead. |
| `[InlineImage index=0]` | Draws one of the four message images at the current printing position. |
| `[Image index=0 pos=85,97 hasBorder=1 alpha=255 fadeAmount=15]` | Draws a message image at a fixed position. |
| `[HideImage fadeAmount=15]` | Hides the current message image. Zero hides it immediately. |
| `[AnimSprite spriteID=0x17 raster=0]` | Draws a single sprite raster in the message. |
| `[Animation spriteID=0x17 rasterIDs=0,1,2 delays=4,4,8]` | Builds a looping sprite animation. Each raster needs a corresponding delay. |

Image tags refer to the list most recently supplied to `SetMessageImages`; they do not load image data by themselves.

### 5.2.5. Text Effects

Effects are written as paired tags. `[/fx]` closes the most recently opened effect, which is convenient when effects are nested:

```star-rod
[Wave]This text moves in a wave.[/Wave]
[Rainbow][SizeWave]Two effects at once![/fx][/fx]
```

| Effect | Description |
| --- | --- |
| `Shake` | Moves characters around randomly. |
| `Wave` | Moves individual characters in a wave. |
| `NoiseOutline` | Draws a noisy outline around the text. |
| `Static` | Blends static into the text: `[Static percent=50]`. |
| `Blur` | Blurs along `x`, `y`, or both axes: `[Blur dir=xy]`. |
| `Rainbow` | Changes individual character colors in a rainbow pattern. |
| `DitherFade` | Dithers the text toward transparency: `[DitherFade percent=50]`. |
| `GlobalWave` | Applies the wave to the line as a whole. |
| `GlobalRainbow` | Applies the rainbow across the message as a whole. |
| `PrintRising` | Prints characters small and lets them rise into place. |
| `PrintGrowing` | Prints characters small and grows them into place. |
| `SizeJitter` | Randomly changes character sizes. |
| `SizeWave` | Changes character sizes in a wave. |
| `DropShadow` | Adds a shadow behind the text. |

## 5.3. Modifying and Adding Messages

Message sources live in `$mod/strings/src/`. Any existing string can be modified or overwritten using message patches in `$mod/strings/patch`. To illustrate, let's modify an existing string: `001C0002` (the tattle for Paragoomba). Create a new file `$mod/strings/patch/example.str` and put the following:

```star-rod
#string:1C:02 {
    [Style Right]
    New Paragoomba tattle![Wait][End]
}
```

You are not limited to existing mesages alone. New messages can be added to any section, including a new section (2F) for custom strings. If you don't care what particular value the ID has, you may also assign a name and let Star Rod resolve it during the build:

```star-rod
#string:2F:(MyStringName) {
    [Style Right]
    This string has a useful name.[Wait][End]
}
```

Refer to it from a patch or script as `~String:MyStringName`.

## 5.4. Local Messages

Messages don't have to use global scope. An anonymous message can be created by declaring it directly in a patch file:

```star-rod
#string $MyExampleString {
    [Style Right]
    This is an embedded string.[Wait][End]
}

#new:Script $ShowExample {
    Call SpeakToPlayer ( .Npc:Self 00000000 00000000 00000000 $MyExampleString )
    Return
    End
}
```

## 5.5. Examples

### 5.5.1. Strings with Variables

Strings may contain variables to create messages like "*You won X coins!*" or "*Do you want to cook with Y?*". The engine has three slots for variables, referenced by `[Var 0]` through `[Var 2]`. Set a variable before displaying the message:

| Type | Script call |
| --- | --- |
| Integer value | `Call SetMessageValue ( value, variableIndex )` |
| Message text | `Call SetMessageString ( string, variableIndex )` |

```star-rod
#string $CoinMessage {
    [Style Narrate]
    You got [Var 0] coins![Wait][End]
}

#new:Script $ShowCoinMessage {
    Call SetMessageValue ( *Var[0] 0 )
    Call SpeakToPlayer ( .Npc:Self 00000000 00000000 00000000 $CoinMessage )
    Return
    End
}
```

Each buffer holds at most 31 encoded characters plus its terminator. `SetMessageString` accepts either a message ID or a pointer to encoded message data.

### 5.5.2. Presenting a Choice

A choice normally uses one string for the question and another for its options. End the question with `[Yield]` so the script can continue without closing the box:

```star-rod
#string $TrialQuestion {
    [Style Right]
    Are you ready for a trial?[Yield][End]
}

#string $TrialChoices {
    [Style Choice pos=96,112 size=128,62][StartChoice]
    [Option 0]Yes[BR]
    [Option 1]No[BR]
    [Option 2]Tell me more
    [EndChoice cancel=1][End]
}
```

Display the question normally, then call `ShowChoice` with the options string. The selected option is returned in `*Var[0]`. Use `ContinueSpeech` with a response beginning with `[Next]` when you want to keep using the same message box.

### 5.5.3. Displaying an Image

Message image tags use an array of `MessageImageData` records. Each record is five words: raster pointer, palette pointer, packed width and height, image format, and bit depth. Install the list with `SetMessageImages` before displaying the message:

```star-rod
#new:IntTable $ImageList {
    $ImageRaster $ImagePalette 00200020 00000002 00000000
}

#new:IntTable $ImageRaster {
    ~RasterFile:CI-4:example.png
}

#new:IntTable $ImagePalette {
    ~PaletteFile:CI-4:example.png
}

#string $ShowImageString {
    [Style Right]
    [Image index=0 pos=85,97 hasBorder=1 alpha=255 fadeAmount=15]
    Look at this![Wait][End]
}

#new:Script $ShowImage {
    Call SetMessageImages ( $ImageList )
    Call SpeakToPlayer ( .Npc:Self 00000000 00000000 00000000 $ShowImageString )
    Return
    End
}
```

Raster and palette files referenced by `~RasterFile` and `~PaletteFile` are loaded from `$mod/res/`.
