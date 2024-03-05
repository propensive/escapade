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

import rudiments.*
import vacuous.*
import fulminate.*
import anticipation.*
import digression.*
import gossamer.*
import hieroglyph.*
import spectacular.*

import language.experimental.captureChecking

object Displayable:
  given output: Displayable[Display] = identity(_)
  given text: Displayable[Text] = text => Display(text)
  given pid: Displayable[Pid] = pid => e"${pid.value.show}"

  given highlighted[ValueType](using highlight: Highlight[ValueType], show: Show[ValueType])
        : Displayable[ValueType] =

    value => e"${highlight.color(value)}(${value.show})"

  given message: Displayable[Message] = _.fold[Display](e""): (acc, next, level) =>
    level match
      case 0 => e"$acc${Fg(0xefe68b)}($next)"
      case 1 => e"$acc$Italic(${Fg(0xffd600)}($next))"
      case _ => e"$acc$Italic($Bold(${Fg(0xffff00)}($next)))"

  given option[T: Displayable]: Displayable[Option[T]] =
    case None    => Display("empty".show)
    case Some(v) => summon[Displayable[T]](v)
  
  given show[ValueType](using show: Show[ValueType]): Displayable[ValueType] = value =>
    Display(show.text(value))

  given exception(using TextMetrics): Displayable[Exception] = e =>
    summon[Displayable[StackTrace]](StackTrace.apply(e))

  given error: Displayable[Error] = _.message.display

  given (using TextMetrics): Displayable[StackTrace] = stack =>
    val methodWidth = stack.frames.map(_.method.method.length).max
    val classWidth = stack.frames.map(_.method.className.length).max
    val fileWidth = stack.frames.map(_.file.length).max
    
    val fullClass = e"$Italic(${stack.component}.$Bold(${stack.className}))"
    val init = e"${Fg(0xffffff)}($fullClass): ${stack.message}"
    
    val root = stack.frames.foldLeft(init):
      case (msg, frame) =>
        val obj = frame.method.className.ends(t"#")
        val drop = if obj then 1 else 0
        val file = e"${Fg(0x5f9e9f)}(${frame.file.fit(fileWidth, Rtl)})"
        val dot = if obj then t"." else t"#"
        val className = e"${Fg(0xc61485)}(${frame.method.className.drop(drop, Rtl).fit(classWidth, Rtl)})"
        val method = e"${Fg(0xdb6f92)}(${frame.method.method.fit(methodWidth)})"
        val line = e"${Fg(0x47d1cc)}(${frame.line.let(_.show).or(t"?")})"
        
        e"$msg\n  ${Fg(0x808080)}(at) $className${Fg(0x808080)}($dot)$method $file${Fg(0x808080)}(:)$line"
    
    stack.cause.option match
      case None        => root
      case Some(cause) => e"$root\n${Fg(0xffffff)}(caused by:)\n$cause"
  
  given (using TextMetrics): Displayable[StackTrace.Frame] = frame =>
    val className = e"${Fg(0xc61485)}(${frame.method.className.fit(40, Rtl)})"
    val method = e"${Fg(0xdb6f92)}(${frame.method.method.fit(40)})"
    val file = e"${Fg(0x5f9e9f)}(${frame.file.fit(18, Rtl)})"
    val line = e"${Fg(0x47d1cc)}(${frame.line.let(_.show).or(t"?")})"
    e"$className${Fg(0x808080)}(#)$method $file${Fg(0x808080)}(:)$line"

  given Displayable[StackTrace.Method] = method =>
    val className = e"${Fg(0xc61485)}(${method.className})"
    val methodName = e"${Fg(0xdb6f92)}(${method.method})"
    e"$className${Fg(0x808080)}(#)$methodName"
  
  given (using decimalizer: Decimalizer): Displayable[Double] = double =>
    Display.make(decimalizer.decimalize(double), _.copy(fg = 0xffd600))

  given Displayable[Throwable] = throwable =>
    Display.make[String](throwable.getClass.getName.nn.show.cut(t".").last.s,
        _.copy(fg = 0xdc133b))

trait Displayable[-ValueType]:
  def apply(value: ValueType): Display
