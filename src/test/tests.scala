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

import probably.*
import rudiments.*
import gossamer.*

import escapes.*

object Tests extends Suite(t"Escapade tests"):
  def run(): Unit =
    suite(t"Rendering tests"):
      test(t"normal string"):
        out"hello world".render
      .assert(_ == t"hello world")
      
      test(t"simple string substitution"):
        out"hello ${"world"}".render
      .assert(_ == t"hello world")
      
      test(t"bold text"):
        out"$Bold{bold} text".render
      .assert(_ == t"\e[1mbold\e[22m text")
      
      test(t"italic text"):
        out"$Italic{italic} text".render
      .assert(_ == t"\e[3mitalic\e[23m text")
      
      test(t"24-bit colored text"):
        out"${iridescence.colors.Tan}[text]".render
      .assert(_ == t"\e[38;2;210;180;139mtext\e[39m")
      
      test(t"non-escape insertion should not parse brackets"):
        val notAnEscape = 42
        out"${notAnEscape}[text]".render
      .assert(_ == t"42[text]")

    suite(t"Escaping tests"):
      test(t"Check that an escaped tab is a tab"):
        out"|\t|".plain
      .assert(_.length == 3)
      
      test(t"Check that a unicode value is converted"):
        out"|\u0040|".plain
      .assert(_ == t"|@|")
      
      test(t"Check that a newline is converted correctly"):
        out"\n".plain
      .assert(_ == t"\n")
