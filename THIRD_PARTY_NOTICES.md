# Third-party notices

LLM Central is free software (see `LICENSE`). It includes the following material from other projects.

(Not third-party, listed so reviewers don't have to ask: the app icon, `artwork/icon.svg` and every icon file made from
it by `tools/icon`, is the project owner's own artwork, under the same licence as the app.)

## Provider logo: duck.ai (DuckDuckGo)

File: `app/src/main/res/drawable/ic_logo_duck.xml`, and the same shape in `app/src/main/assets/settings.html`
(`LOGOS`, original path).

Source: [Simple Icons](https://simpleicons.org) 16.33.0 (`simple-icons` npm package, file `icons/duckduckgo.svg`),
released under **CC0 1.0 Universal** (public domain dedication; no attribution required, credited here anyway).
For Android the path was rewritten without arcs (same shape, 3 decimals) and split into three paths.

The logo is a trademark of DuckDuckGo. LLM Central uses it only to show that an account is duck.ai, DuckDuckGo's
chat service; it is not affiliated with or endorsed by DuckDuckGo.

## Provider logos (ChatGPT, Claude, DeepSeek, Z.ai)

Files: `app/src/main/res/drawable/ic_logo_chatgpt.xml`, `ic_logo_claude.xml`, `ic_logo_deepseek.xml`,
`ic_logo_zai.xml`, and the same shapes in `app/src/main/assets/settings.html` (`LOGOS`, original paths).

Source: [Lobe Icons](https://github.com/lobehub/lobe-icons) 1.95.1 (`@lobehub/icons-static-svg`,
files `icons/openai.svg`, `icons/claude.svg`, `icons/deepseek.svg`, `icons/zai.svg`). For Android the path data
was rewritten without arcs, in relative commands (same shapes), so Android's vector reader handles it.

The logos are trademarks of their owners (OpenAI, Anthropic, DeepSeek, Z.ai / Zhipu AI). LLM Central uses them only to
show which service an account belongs to; it is not affiliated with or endorsed by these companies.

```
MIT License

Copyright (c) 2023 LobeHub

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
