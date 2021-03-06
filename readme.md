[<img alt="GitHub Workflow" src="https://img.shields.io/github/workflow/status/propensive/escapade/Build/main?style=for-the-badge" height="24">](https://github.com/propensive/escapade/actions)
[<img src="https://img.shields.io/maven-central/v/com.propensive/escapade-core?color=2465cd&style=for-the-badge" height="24">](https://search.maven.org/artifact/com.propensive/escapade-core)
[<img src="https://img.shields.io/discord/633198088311537684?color=8899f7&label=DISCORD&style=for-the-badge" height="24">](https://discord.gg/7b6mpF6Qcf)
<img src="/doc/images/github.png" valign="middle">

# Escapade

__Escapade__ makes it easy to work safely with strings containing ANSI escape codes.

## Features

- provides a representation of strings containing ANSI escape codes
- support for 24-bit color, integrated with [Iridescence](https://github.com/propensive/iridescence)
  color representations
- introduces the `ansi""` string interpolator
- constant-time reduction to "plain" strings and printed length
- extensible support for different substitution types
- introduces "virtual" escapes with stack-based region tracking

## Availability

The current latest release of Escapade is __0.4.0__.

## Getting Started

## About ANSI Codes

[ANSI escape codes](https://en.wikipedia.org/wiki/ANSI_escape_code) provide a variety of features in
terminal emulators for performing operations such as positioning the cursor, changing the appearance
of text with styles like bold, italic and strike-through, as well as foreground and background
colors.

## Creating ANSI strings

To create an ANSI string, we use the `ansi""` interpolator. This works like an ordinary string
interpolator, allowing substitutions of stringlike values.

But substitutions may also be `Escape` instances, which do not insert any visible characters, but
may change the appearance or style of subsequent characters. Many `Escape` instances are defined in
the `escapes` object, for example, `Bold`, `Underline` and `BrightRedFg`.

These escapes may be used in an ANSI string, like so:
```scala
import escapes.*
val txt = ansi"This text is ${Bold}bold, ${Underline}underlined and ${BrightRedFg}bright red."
```

This introduces the bold and underline styles and the bright red color to the string, but once
introduced, they continue indefinitely.

Thankfully, Escapade provides a convenient way to terminate styles introduced by ANSI escapes. If
the character immediately following the escape is a recognized opening bracket (`(`, `[`, `{`, `
?`,
`<`), then the style will continue until a corresponding closing brace is found in the string, i.e.
`)`, `]`, `}`, `
?` or `>`.

For example,
```scala
ansi"This text is $Bold[bold], $Underline{underlined} and $BrightRedFg<bright red>."
```
will apply each style only to the words inside the brackets.

Plenty of choice is given over which type of brackets to use, so that a choice can (hopefully) be
made which does not conflict with the real content of the string. Regions may be nested arbitrarily
deep, and different bracketing styles may be used, but nested regions form a stack which must be
terminated in order. So any closing bracket other than the type corresponding to the most recent
opening bracket will not be given special treatment.

For example,
```scala
ansi"This text is $Bold[bold and $Italic{italic] text.}"
```
might be intending to display the final word, `text`, in italic but not bold. But the mismatched
brackets would treat `italic] text.` as literal text, rendered in italic. And, in fact, the ANSI
string would not compile due to the unclosed `[` bracket.

## Combining colors

While styles such as _bold_, _italic_, _underline_ and _reverse_ may be combined independently in a
string, the situation is more complex with colors, as a new color simply replaces an old one, and
it is not normally possible to restore the previous color; only to "reset" the color.

Indeed, this is what happens using the standard ANSI escapes provided in the `escapes` object.

But Escapade also provides stack-based tracking for colored text, so that regions may be nested, and
the underlying color may be restored. This uses colors from
[Iridescence](https://github.com/propensive/iridescence/) which may be substituted straight into an
ANSI string, like so:

```scala
import iridescence.*
import colors.*
ansi"$Gold[gold, $Indigo[indigo, $HotPink[hot pink], indigo] $White[and] gold]"
```

## Manipulating colors

Each substitution into an `ansi""` string interpolator may apply a change to the existing style,
represented by and tracked throughout the string as an instance of the case class, `Style`.
Typically, these changes will specify the new state of properties such as _bold_, _italic_ or the
background color.

But the changes may also be a transformation of the existing style information. For example, the
bold state could be flipped depending on what it was before, or the foreground color could be
mixed with black to give a "faded" or "darkened" effect to the text, without changing its hue.

Any such transformation requires an object to be used as a substitution to an interpolated string
to introduce it, plus a corresponding contextual `Stylize` instance, for example:
```scala
case class Fade(amount: Double)

given Stylize[Fade] = fade =>
  Stylize { style =>
    style.copy(fg = style.fg.hsv.shade(fade.amount).srgb)
  }
```


## Related Projects

The following _Scala One_ libraries are dependencies of _Escapade_:

[![Gossamer](https://github.com/propensive/gossamer/raw/main/doc/images/128x128.png)](https://github.com/propensive/gossamer/) &nbsp; [![Iridescence](https://github.com/propensive/iridescence/raw/main/doc/images/128x128.png)](https://github.com/propensive/iridescence/) &nbsp;

The following _Scala One_ libraries are dependents of _Escapade_:

[![Escritoire](https://github.com/propensive/escritoire/raw/main/doc/images/128x128.png)](https://github.com/propensive/escritoire/) &nbsp; [![Eucalyptus](https://github.com/propensive/eucalyptus/raw/main/doc/images/128x128.png)](https://github.com/propensive/eucalyptus/) &nbsp; [![Harlequin](https://github.com/propensive/harlequin/raw/main/doc/images/128x128.png)](https://github.com/propensive/harlequin/) &nbsp;

## Status

Escapade is classified as __fledgling__. Propensive defines the following five stability levels for open-source projects:

- _embryonic_: for experimental or demonstrative purposes only, without any guarantees of longevity
- _fledgling_: of proven utility, seeking contributions, but liable to significant redesigns
- _maturescent_: major design decisions broady settled, seeking probatory adoption and refinement
- _dependable_: production-ready, subject to controlled ongoing maintenance and enhancement; tagged as version `1.0` or later
- _adamantine_: proven, reliable and production-ready, with no further breaking changes ever anticipated

Escapade is designed to be _small_. Its entire source code currently consists of 365 lines of code.

## Building

Escapade can be built on Linux or Mac OS with Irk, by running the `irk` script in the root directory:
```sh
./irk
```

This script will download `irk` the first time it is run, start a daemon process, and run the build. Subsequent
invocations will be near-instantaneous.

## Contributing

Contributors to Escapade are welcome and encouraged. New contributors may like to look for issues marked
<a href="https://github.com/propensive/escapade/labels/good%20first%20issue"><img alt="label: good first issue"
src="https://img.shields.io/badge/-good%20first%20issue-67b6d0.svg" valign="middle"></a>.

We suggest that all contributors read the [Contributing Guide](/contributing.md) to make the process of
contributing to Escapade easier.

Please __do not__ contact project maintainers privately with questions. While it can be tempting to repsond to
such questions, private answers cannot be shared with a wider audience, and it can result in duplication of
effort.

## Author

Escapade was designed and developed by Jon Pretty, and commercial support and training is available from
[Propensive O&Uuml;](https://propensive.com/).



## Name

An __escapade__ is a "wild and exciting undertaking" which is "not necessarily lawful"; like the variety of _escape_ codes that are only valid inside an ANSI terminal.

## License

Escapade is copyright &copy; 2021-22 Jon Pretty & Propensive O&Uuml;, and is made available under the
[Apache 2.0 License](/license.md).
