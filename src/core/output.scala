/*
    Escapade, version [unreleased]. Copyright 2024 Jon Pretty, Propensive OÜ.

    The primary distribution site is: https://propensive.com/

    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
    file except in compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software distributed under the
    License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
    either express or implied. See the License for the specific language governing permissions
    and limitations under the License.
*/

package escapade

import gossamer.*
import rudiments.*
import vacuous.*
import fulminate.*
import anticipation.*
import contingency.*
import symbolism.*
import turbulence.*
import contextual.*
import spectacular.*

import scala.util.*

import language.experimental.pureFunctions

opaque type CharSpan = Long

object CharSpan:
  def apply(start: Int, end: Int): CharSpan = (start.toLong << 32) + (Int.MaxValue - end)
  given Ordering[CharSpan] = Ordering.Long.on[CharSpan](identity(_))
  val Nowhere: CharSpan = CharSpan(Int.MaxValue, Int.MaxValue)

extension (span: CharSpan)
  def start: Int = (span >> 32).toInt
  def end: Int = Int.MaxValue - span.toInt
  def isEmpty: Boolean = start == end

  def trimLeft(n: Int): CharSpan =
    if n >= end then CharSpan.Nowhere else if n <= start then CharSpan(start - n, end - n)
    else CharSpan(0, end - n)

  def takeLeft(n: Int): CharSpan =
    if n <= start then CharSpan.Nowhere else if n >= end then span else CharSpan(start, n)

  def shift(n: Int): CharSpan = CharSpan(start + n, end + n)

object TextStyle:
  val esc: Char = 27.toChar

case class TextStyle
    (fg:        Optional[Int] = Unset,
     bg:        Optional[Int] = Unset,
     italic:    Boolean       = false,
     bold:      Boolean       = false,
     reverse:   Boolean       = false,
     underline: Boolean       = false,
     conceal:   Boolean       = false,
     strike:    Boolean       = false):

  import escapes.*
  import TextStyle.esc

  private def italicEsc: Text = if italic then styles.Italic.on else styles.Italic.off
  private def boldEsc: Text = if bold then styles.Bold.on else styles.Bold.off
  private def reverseEsc: Text = if reverse then styles.Reverse.on else styles.Reverse.off
  private def underlineEsc: Text = if underline then styles.Underline.on else styles.Underline.off
  private def concealEsc: Text = if conceal then styles.Conceal.on else styles.Conceal.off
  private def strikeEsc: Text = if strike then styles.Strike.on else styles.Strike.off

  def addChanges(buf: StringBuilder, next: TextStyle, colorDepth: ColorDepth): Unit =
    if fg != next.fg then buf.add(next.fg.let(Fg(_).ansi(colorDepth)).or(t"$esc[39m"))
    if bg != next.bg then buf.add(next.bg.let(Bg(_).ansi(colorDepth)).or(t"$esc[49m"))
    if italic != next.italic then buf.add(t"${esc}${next.italicEsc}")
    if bold != next.bold then buf.add(t"${esc}${next.boldEsc}")
    if reverse != next.reverse then buf.add(t"${esc}${next.reverseEsc}")
    if underline != next.underline then buf.add(t"${esc}${next.underlineEsc}")
    if conceal != next.conceal then buf.add(t"${esc}${next.concealEsc}")
    if strike != next.strike then buf.add(t"${esc}${next.strikeEsc}")

object Stylize:
  def apply(lambda: TextStyle => TextStyle): Ansi.Input.Markup = Ansi.Input.Markup(lambda)

trait Ansi2:

  class DisplaySubstitution[ValueType](display: ValueType => Display)
  extends Substitution[Ansi.Input, ValueType, "t"]:
    def embed(value: ValueType) = Ansi.Input.TextInput(display(value))

  inline given [ValueType] => Substitution[Ansi.Input, ValueType, "t"] as display =
    val display: ValueType => Display = value => compiletime.summonFrom:
      case display: Displayable[ValueType] => display(value)
      case given Show[ValueType]           => Display(value.show)

    DisplaySubstitution[ValueType](display)

object Ansi extends Ansi2:
  type Transform = TextStyle => TextStyle

  def strip(txt: Text): Text = txt.sub(t"""\e\\[?.*?[\\@-~]""", t"")

  given Stylize[Escape] = identity(_)

  given [ColorType: Chromatic as color] => Stylize[ColorType] =
    color => Stylize(_.copy(fg = color.asRgb24Int))

  given Stylize[Bg] = bgColor => Stylize(_.copy(bg = bgColor.color))
  given Stylize[Fg] = fgColor => Stylize(_.copy(fg = fgColor.color))

  given Stylize[Bold.type] = _ => Stylize(_.copy(bold = true))
  given Stylize[Italic.type] = _ => Stylize(_.copy(italic = true))
  given Stylize[Underline.type] = _ => Stylize(_.copy(underline = true))
  given Stylize[Strike.type] = _ => Stylize(_.copy(strike = true))
  given Stylize[Conceal.type] = _ => Stylize(_.copy(conceal = true))
  given Stylize[Reverse.type] = _ => Stylize(_.copy(reverse = true))

  enum Input:
    case TextInput(text: Display)
    case Markup(transform: Transform)
    case Escape(on: Text, off: Text)

  case class Frame(bracket: Char, start: Int, transform: Transform)

  case class State
      (text:       Text                         = t"",
       last:       Option[Transform]            = None,
       stack:      List[Frame]                  = Nil,
       spans:      TreeMap[CharSpan, Transform] = TreeMap(),
       insertions: TreeMap[Int, Text]           = TreeMap()):

    def add(span: CharSpan, transform: Transform): State =
      copy(spans = spans.updated(span, spans.get(span).fold(transform)(transform.andThen(_))))

    def add(pos: Int, esc: Escape): State =
      val insertions2 = insertions.get(pos).fold(t"\e"+esc.on)(_+t"\e"+esc.on)
      copy(insertions = insertions.updated(pos, insertions2))

  object Interpolator extends contextual.Interpolator[Input, State, Display]:
    private val complement = Map('[' -> ']', '(' -> ')', '{' -> '}', '<' -> '>', '«' -> '»')
    def initial: State = State()

    def parse(state: State, text: Text): State =
      state.last.fold(closures(state, text)): transform =>
        text.at(0) match
          case '\\' =>
            closures(state.copy(last = None), text.drop(1))
          case '[' | '(' | '<' | '«' | '{' =>
            val frame = Frame(complement(text.at(0).vouch(using Unsafe)), state.text.length, transform)
            closures(state.copy(stack = frame :: state.stack, last = None), text.drop(1))

          case _ =>
            val state2 = state.add(CharSpan(state.text.length, state.text.length), transform)
            closures(state2.copy(last = None), text)

    private def closures(state: State, text: Text): State =
      try state.stack.headOption.fold(state.copy(text = state.text+TextEscapes.escape(text))): frame =>
        safely(text.where(_ == frame.bracket)) match
          case Unset =>
            state.copy(text = state.text+text)

          case idx: Int =>
            val text2 = state.text+text.take(idx)
            val span2: CharSpan = CharSpan(frame.start, state.text.length + idx)
            val state2: State = state.add(span2, frame.transform)
            val state3: State = state2.copy(text = text2, last = None, stack = state.stack.tail)
            closures(state3, text.drop(idx + 1))
      catch case error: EscapeError => error match
        case EscapeError(message) => throw InterpolationError(message)

    def insert(state: State, value: Input): State = value match
      case Input.TextInput(text) =>
        val textSpans: TreeMap[CharSpan, Transform] = text.spans.map:
          case (span, transform) => (span.shift(state.text.length): CharSpan) -> transform

        val textInsertions: TreeMap[Int, Text] = text.insertions.map:
          case (pos, ins) => (pos + state.text.length) -> ins

        state.copy(text = state.text+text.plain, last = None, spans = state.spans ++ textSpans,
            insertions = state.insertions ++ textInsertions)

      case Input.Markup(transform) =>
        state.copy(last = Some(transform))

      case esc@Input.Escape(on, off) =>
        state.copy(last = None).add(state.text.length, esc)

    def skip(state: State): State = insert(state, Input.TextInput(Display.empty))

    def complete(state: State): Display =
      if !state.stack.isEmpty
      then throw InterpolationError(msg"the closing brace does not match an opening brace")

      Display(state.text, state.spans, state.insertions)

object Display:
  given (using NotGiven[Display is Textual]) => Display is Addable[Display] as add:
    type Result = Display
    inline def add(left: Display, right: Display): Display = left.append(right)

  given appendableOut(using stdio: Stdio): SimpleAppendable[Out.type, Display] = (out, output) =>
    stdio.print(output.render(stdio.termcap))

  given appendableErr(using stdio: Stdio): SimpleAppendable[Err.type, Display] = (err, output) =>
    stdio.printErr(output.render(stdio.termcap))

  given appendable[TargetType](using appendable: Appendable[TargetType, Text]): Appendable[TargetType, Display] =
    (target, output) => appendable.append(target, output.map(_.render(termcapDefinitions.basic)))

  given writable[TargetType](using writable: Writable[TargetType, Text]): Writable[TargetType, Display] =
    (target, output) => writable.write(target, output.map(_.render(termcapDefinitions.basic)))

  given Display is Textual:
    type Show[-ValueType] = Displayable[ValueType]
    def classTag: ClassTag[Display] = summon[ClassTag[Display]]
    def text(display: Display): Text = display.plain
    def length(text: Display): Int = text.plain.s.length
    def apply(text: Text): Display = Display(text)

    def map(text: Display, lambda: Char => Char): Display =
      Display(Text(text.plain.s.map(lambda)), text.spans, text.insertions)

    def range(text: Display, start: Int, end: Int): Display = text.dropChars(start).takeChars(end - start)
    val empty: Display = Display.empty
    def concat(left: Display, right: Display): Display = left.append(right)
    def unsafeChar(text: Display, index: Int): Char = text.plain.s.charAt(index)
    def indexOf(text: Display, sub: Text): Int = text.plain.s.indexOf(sub.s)
    def show[ValueType](value: ValueType)(using display: Displayable[ValueType]) = display(value)

  val empty: Display = Display(t"")
  given joinable: Joinable[Display] = _.fold(empty)(_ + _)
  given Display is Printable as printable = _.render(_)

  given cuttable: Cuttable[Display, Text] = (text, delimiter, limit) =>
    import java.util.regex.*
    val pattern = Pattern.compile(t"(.*)${Pattern.quote(delimiter.s).nn}(.*)".s).nn

    @tailrec
    def recur(source: Display, limit: Int, acc: List[Display]): List[Display] =
      if limit <= 0 then acc
      else
        val matcher = pattern.matcher(source.plain.s).nn
        if matcher.matches
        then
          val output = source.take(matcher.group(2).nn.length, Rtl)
          recur(source.take(matcher.group(1).nn.length), limit - 1, output :: acc)
        else source :: acc

    IArray.from(recur(text, limit, Nil))

  given Ordering[Display] = Ordering.by(_.plain)

  def make[ValueType](value: ValueType, transform: Ansi.Transform)(using Show[ValueType]): Display =
    val text: Text = value.show
    Display(text, TreeMap(CharSpan(0, text.s.length) -> transform))

case class Display
    (plain: Text, spans: TreeMap[CharSpan, Ansi.Transform] = TreeMap(),
        insertions: TreeMap[Int, Text] = TreeMap()):

  def explicit: Text = render(termcapDefinitions.xtermTrueColor).flatMap: char =>
    if char.toInt == 27 then t"\\e" else char.show

  @targetName("add")
  def append(text: Text): Display = Display(t"$plain$text", spans)

  @targetName("add2")
  def append(text: Display): Display =
    val newSpans: TreeMap[CharSpan, Ansi.Transform] = text.spans.map:
      case (span, transform) => (span.shift(plain.length): CharSpan) -> transform

    Display(plain+text.plain, spans ++ newSpans)

  def dropChars(n: Int, dir: Bidi = Ltr): Display = dir match
    case Rtl =>
      takeChars(plain.length - n)

    case Ltr =>
      val newSpans: TreeMap[CharSpan, Ansi.Transform] =
        spans.map:
          case (span, transform) =>
            val charSpan: CharSpan = span.trimLeft(n)
            charSpan -> transform
        .view.filterKeys { k => k.isEmpty || k != CharSpan.Nowhere }.to(TreeMap)

      Display(plain.drop(n), newSpans)

  def takeChars(n: Int, dir: Bidi = Ltr): Display = dir match
    case Rtl =>
      dropChars(plain.length - n)

    case Ltr =>
      val newSpans: TreeMap[CharSpan, Ansi.Transform] =
        spans.map:
          case (span, tf) =>
            val charSpan: CharSpan = span.takeLeft(n)
            charSpan -> tf
        .view.filterKeys { k => k.isEmpty || k != CharSpan.Nowhere }.to(TreeMap)

      Display(plain.take(n), newSpans)

  def render(termcap: Termcap): Text =
    val buf = StringBuilder()

    @tailrec
    def recur
        (spans:      TreeMap[CharSpan, Ansi.Transform],
         pos:        Int                               = 0,
         style:      TextStyle                         = TextStyle(),
         stack:      List[(CharSpan, TextStyle)]       = Nil,
         insertions: TreeMap[Int, Text]                = TreeMap())
            : Text =

      inline def addSpan(): Text =
        val newInsertions = addText(pos, spans.head(0).start, insertions)
        val newStyle = spans.head(1)(style)
        style.addChanges(buf, newStyle, termcap.color)
        val newStack = if spans.head(0).isEmpty then stack else (spans.head(0) -> style) :: stack
        recur(spans.tail, spans.head(0).start, newStyle, newStack, newInsertions)

      @tailrec
      def addText(from: Int, to: Int, insertions: TreeMap[Int, Text]): TreeMap[Int, Text] =
        if insertions.isEmpty then
          buf.add(plain.slice(from, to))
          insertions
        else if insertions.head(0) < to then
          buf.add(plain.slice(pos, insertions.head(0)))
          buf.add(insertions.head(1))
          addText(insertions.head(0), to, insertions.tail)
        else
          buf.add(plain.slice(from, to))
          insertions

      if stack.isEmpty then
        if spans.isEmpty then
          val remaining = addText(pos, plain.length, insertions)
          remaining.values.each(buf.add(_))
          buf.text
        else addSpan()
      else
        if spans.isEmpty || stack.head(0).end <= spans.head(0).start then
          val newInsertions = addText(pos, stack.head(0).end, insertions)
          val newStyle = stack.head(1)
          style.addChanges(buf, newStyle, termcap.color)
          recur(spans, stack.head(0).end, newStyle, stack.tail, newInsertions)
        else addSpan()

    if termcap.ansi then recur(spans, insertions = insertions) else plain

object Bold
object Italic
object Underline
object Strike
object Reverse
object Conceal

object Bg:
  def apply[ColorType: Chromatic](color: ColorType): Bg = Bg(color.asRgb24Int)

case class Bg(color: Int):
  def fg: Fg = Fg(color)

  def highContrast: Fg =
    Fg(if ((color&255)*0.07 + ((color >> 8)&255)*0.72 + ((color >> 16)&255)*0.21) > 128 then 0 else 16777215)

  def ansi(colorDepth: ColorDepth): Text =
    val red = (color >> 16)&255
    val green = (color >> 8)&255
    val blue = color&255

    colorDepth match
      case ColorDepth.TrueColor =>
        t"\e[48;2;$red;$green;${blue}m"

      case _ =>
        val n = 16 +
          (if blue == red && red == green then 216 + red*23/255 else red*5/255*36 + green*5/255*6 + blue*5/255)

        t"\e[48;5;${n}m"

case class Fg(color: Int):
  def bg: Bg = Bg(color)
  def ansi(colorDepth: ColorDepth): Text =
    val red = (color >> 16)&255
    val green = (color >> 8)&255
    val blue = color&255

    colorDepth match
      case ColorDepth.TrueColor =>
        t"\e[38;2;$red;$green;${blue}m"

      case _ =>
        val n = 16 +
          (if blue == red && red == green then 216 + red*23/255 else red*5/255*36 + green*5/255*6 + blue*5/255)

        t"\e[38;5;${n}m"

type Stylize[T] = Substitution[Ansi.Input, T, "esc"]

object Highlight:
  def apply[ValueType](using DummyImplicit)[ColorType: Chromatic](color: ColorType): Highlight[ValueType] =
    value => Fg(color.asRgb24Int)

  def apply[ValueType](using DummyImplicit)[ColorType: Chromatic](chooseColor: ValueType -> ColorType)
        : Highlight[ValueType] =

    value => Fg(chooseColor(value).asRgb24Int)

trait Highlight[-ValueType]:
  def color(value: ValueType): Fg
