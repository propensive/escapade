/*
    Escapade, version [unreleased]. Copyright 2023 Jon Pretty, Propensive OÜ.

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
import digression.*
import gossamer.*
import hieroglyph.*
import spectacular.*
import iridescence.*

object Display:
  given output: Display[Output] = identity(_)
  given text: Display[Text] = text => Output(text)
  given pid: Display[Pid] = pid => out"${pid.value.show}"

  given message: Display[Message] = _.fold[Output](out""): (acc, next, level) =>
    level match
      case 0 => out"$acc${colors.Khaki}($next)"
      case 1 => out"$acc$Italic(${colors.Gold}($next))"
      case _ => out"$acc$Italic($Bold(${colors.Yellow}($next)))"

  given option[T: Display]: Display[Option[T]] =
    case None    => Output("empty".show)
    case Some(v) => summon[Display[T]](v)
  
  given show[ValueType](using show: Show[ValueType]): Display[ValueType] = value =>
    Output(show(value))

  given exception(using TextWidthCalculator): Display[Exception] = e =>
    summon[Display[StackTrace]](StackTrace.apply(e))

  given error: Display[Error] = _.message.out

  given (using TextWidthCalculator): Display[StackTrace] = stack =>
    val methodWidth = stack.frames.map(_.method.method.length).max
    val classWidth = stack.frames.map(_.method.className.length).max
    val fileWidth = stack.frames.map(_.file.length).max
    
    val fullClass = out"$Italic(${stack.component}.$Bold(${stack.className}))"
    val init = out"${colors.White}($fullClass): ${stack.message}"
    
    val root = stack.frames.foldLeft(init):
      case (msg, frame) =>
        val obj = frame.method.className.ends(t"#")
        import colors.*
        val drop = if obj then 1 else 0
        val file = out"$CadetBlue(${frame.file.fit(fileWidth, Rtl)})"
        val dot = if obj then t"." else t"#"
        val className = out"$MediumVioletRed(${frame.method.className.drop(drop, Rtl).fit(classWidth, Rtl)})"
        val method = out"$PaleVioletRed(${frame.method.method.fit(methodWidth)})"
        val line = out"$MediumTurquoise(${frame.line.mm(_.show).or(t"?")})"
        
        out"$msg\n  $Gray(at) $className$Gray($dot)$method $file$Gray(:)$line"
    
    stack.cause.option match
      case None        => root
      case Some(cause) => out"$root\n${colors.White}(caused by:)\n$cause"
  
  given (using TextWidthCalculator): Display[StackTrace.Frame] = frame =>
    import colors.*
    val className = out"$MediumVioletRed(${frame.method.className.fit(40, Rtl)})"
    val method = out"$PaleVioletRed(${frame.method.method.fit(40)})"
    val file = out"$CadetBlue(${frame.file.fit(18, Rtl)})"
    val line = out"$MediumTurquoise(${frame.line.mm(_.show).or(t"?")})"
    out"$className$Gray(#)$method $file$Gray(:)$line"

  given Display[StackTrace.Method] = method =>
    import colors.*
    val className = out"$MediumVioletRed(${method.className})"
    val methodName = out"$PaleVioletRed(${method.method})"
    out"$className$Gray(#)$methodName"
  
  given (using decimalizer: Decimalizer): Display[Double] = double =>
    Output.make(decimalizer.decimalize(double), _.copy(fg = colors.Gold.asInt))

  given Display[Throwable] = throwable =>
    Output.make[String](throwable.getClass.getName.nn.show.cut(t".").last.s,
        _.copy(fg = colors.Crimson.asInt))

trait Display[-ValueType] extends Showable[ValueType]:
  def show(value: ValueType): Text = apply(value).plain
  def apply(value: ValueType): Output
