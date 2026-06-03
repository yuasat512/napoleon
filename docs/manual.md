**English** | [日本語](./manual.ja.md)

# User Manual

Operating instructions for the Napoleon (5-player trick-taking) Swing app. For game rules, see [game-spec.md](game-spec.md).

> The in-game UI is in Japanese. This manual gives the corresponding Japanese strings where they appear on screen.

## 1. Launch

| Method | Command |
|--------|---------|
| Dev run | `./gradlew run` |
| Build Fat JAR | `./gradlew shadowJar` (`build/libs/napoleon-all.jar`) |
| Run Fat JAR | Double-click `Napoleon.vbs` |

JDK 17 or later is required. On exit, `napoleon.properties` (settings and cumulative stats) is saved. Any uncaught exceptions are appended to `error.txt`.

## 2. Screen Layout

![Screenshot](images/screen-layout.jpg)

- **Bottom center**: your hand (Player a), face-up.
- **Middle left / Top left / Top right / Middle right**: the other players (b / c / d / e), face-down.
- **Top center**: the kitty (3 cards) right after the deal.
- **Bottom-left corner**: the kitty after the return. Honor cards are displayed face-up; the trick-1 winner takes them when trick 1 ends. Number cards and the Joker stay face-down until the end of the game.
- **Center**: cards on the table for the current trick.
- **Bottom right**: captured honor cards (a 4-suit × 5-rank grid).
- **Bottom-left info panel**: game number, bid (suit + count), adjutant card, and Napoleon's army / Allied army honor counts. While the Adjutant is undisclosed, the honor counts are shown as a range.
- **Each player's side panel**: name / cumulative score (cyan, light pink if negative) / honor cards captured. Napoleon is marked with the trump-suit icon, and a revealed Adjutant with a star.
- **Speech bubble**: a contextual message appears in each phase.
  - Bidding: 「(suit) N枚」 (suit + count) / 「パス」 (Pass)
  - Redeal: 「配り直し」 (Redeal)
  - Adjutant designation: 「副官は…」 (e.g., 「副官はマイティ」 announces the Mighty as the adjutant card)
  - Trick play: 「マイティ」 (Mighty), 「正ジャック」 (Right Bower), 「裏ジャック」 (Left Bower), 「ジョーカー」 (Joker), 「よろめき」 (Slip), 「チェック」 (check), 「セイム」 (Same); 「(suit) 請求」 — the suit declared after leading the Joker; 「ジョーカー請求」 — leading the Clubs 3 (Joker Call)
  - Result: 「完全勝利！」 (Perfect Win), 「勝利！」 (Win), 「敗北…」 (Loss)

## 3. Menu 「ゲーム」 (Alt+G)

| Item | Shortcut | Action |
|------|:--------:|--------|
| 開始 (Start) | S | Start a new session |
| タイトルに戻る (Return to title) | T | Discard the current game (exits replay mode if active) |
| リプレイ... (Replay...) | L | Replay one game from the log ([§6](#6-replay)) |
| 成績表... (Statistics...) | R | Show cumulative results ([§7](#7-statistics)) |
| 設定... (Settings...) | O | Open the settings dialog ([§5](#5-settings)) |
| 終了 (Exit) | X | Quit the app |

## 4. Play Controls

### 4-1. Common

When the wait time is `0`, advance to the next step after each animation by **clicking the screen or pressing any key**. When `> 0`, the game advances automatically.

### 4-2. Bidding

A vertically stacked button dialog. Choose 「パス」 (Pass) or 「(suit) N枚」 (suit + count). Each suit's button shows the lowest count that can be bid in that suit; a suit whose minimum would exceed the maximum bid (Spades 20) is disabled.

- ↑↓ ←→: cycle through enabled buttons
- Enter / Space: confirm
- Closing with ✕: treated as a pass

### 4-3. Adjutant Designation

Select via radio buttons.

- **ジョーカー / マイティ / 正ジャック / 裏ジャック** (Joker / Mighty / Right Bower / Left Bower): single click
- **その他** (Other): choose a suit and a rank (10 shown as T) from the dropdown menus
- Press Enter or click OK to confirm

### 4-4. Kitty Draw and Return

Napoleon adds the 3 kitty cards to hand (now 13 cards), then returns 3 cards back to the kitty.

- Mouse hover / ←→: select a card
- Left click / Space / Enter / ↓: add the selected card to the return set (it lifts up); confirms once 3 cards are chosen
- Click a lifted card again: deselect it
- Right click / ↑: clear all selections and sort the hand in ascending order

### 4-5. Trick Play

- Mouse hover / ←→: only cards allowed by the follow-suit rule can be selected
- Left click / Space / Enter / ↓: confirm
- **When leading the Joker**: a suit-declaration dialog opens next (except on the final trick)

## 5. Settings

| Item | Default | Description |
|------|:-------:|-------------|
| ゲーム数 (Number of games) | 5 | Games per session. 0 for unlimited |
| 待ち時間 (Wait time) | 600 | Animation step duration in milliseconds. 0 for manual advance |
| 自動プレイ (Auto play) | OFF | The AI also controls Player a |
| 最終トリックまで続行 (Continue until final trick) | OFF | Continue through trick 10 even after the outcome is decided |
| ゲームログ (Game log) | ON | Append game records to `log/napoleon.txt` |

## 6. Replay

Selects and replays one game from the log.

1. Menu → 「リプレイ...」 → choose a log file (default: `log/napoleon.txt`).
2. Pick one game from the list.
3. Replay always uses manual advance, regardless of the wait-time setting. Advance to the next step after each animation by **clicking the screen or pressing any key**.
4. The replay ends when the game finishes, or via Menu → 「タイトルに戻る」.

## 7. Statistics

Counts are aggregated across 6 patterns (**standard / solo × Perfect Win / Win / Loss**) per role.

- **Rows (6)**: standard Perfect Win / Win / Loss, solo Perfect Win / Win / Loss
- **Columns (3)**: Napoleon / Adjutant / Allied army (the Adjutant column shows "-" for solo rows)
- **Bottom row**: the win rate for each column (role)

The summary shows the overall win rate, together with the average score in the **cumulative view** (Menu → 「成績表」) or the session's score and ranking at **session end**.

## 8. Files

| Path | Purpose |
|------|---------|
| `napoleon.properties` | Settings and cumulative stats (loaded on startup, saved on exit) |
| `log/napoleon.txt` | Game log (appended when 「ゲームログ」 is ON; format described in [§9](#9-log-format)) |
| `error.txt` | Exception stack traces |

## 9. Log Format

`log/napoleon.txt` is fixed-width text and human-readable. Each game is appended as the following block.

```
=== 2026-01-01 12:34:56          Timestamp
 a   b   c   d   e               Players (seating order)
D12 H12  -   -  S12              Bidding (each column = a player; wraps at 5 cells)
D13  -   -   -  S13              "Sn" = bid n in spades; "-" = pass
...
---
D17 @A                           Confirmed bid + adjutant card
CJ DT C9 -> CJ H7 HT             Kitty drawn -> kitty returned
---
a:NP  b:AL  c:AL  d:AL  e:AD | a:NP  b:AL  c:AL  d:AL  e:AD
[+J]   D6    D8    D4    D2  |  3(3)  0     0     0     0
 DT    SQ    ST    S4   [S3] |  6(3)  0     0     0     3
                                 Header: roles (NP = Napoleon / AD = Adjutant / AL = Allied army)
                                 Left of "|" (each column = a player): cards played in the trick (lead in [])
                                 Right of "|": cumulative honor counts ((n) = honors gained this trick)
...
---                              (Only when the game ended before the final trick)
 C8    D2    DK    DA    D6      Unplayed remaining hands
 S6    CQ    CA    H8    D7
---
 +6    -4    -4    -4    +6      Score delta per player
```

### Card Notation

| Symbol | Meaning |
|--------|---------|
| `Sx` `Hx` `Dx` `Cx` | Suit + rank (`2`–`9`, `T`, `J`, `Q`, `K`, `A`) |
| `@A` | Mighty (Spades A) |
| `**` | Joker |
| `S*` `H*` `D*` `C*` | Joker led with a declared suit |
| `+J` | Right Bower (the trump J) |
| `-J` | Left Bower (the J of the same color as the trump but a different suit) |
| `@Q` | Slip (Hearts Q). Used only in tricks where the Mighty is also played |
