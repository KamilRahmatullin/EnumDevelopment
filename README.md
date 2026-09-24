<div align="center">

# EnumDevelopment

**Server-side search &amp; replace for Bukkit/Spigot — text, gradients, JSON and even items, straight from the game.**

[![Version](https://img.shields.io/badge/version-1.0.6-44D7B6?style=flat-square)](pom.xml)
[![API](https://img.shields.io/badge/Spigot%20API-1.16-orange?style=flat-square)](https://www.spigotmc.org/)
[![Java](https://img.shields.io/badge/Java-8%2B-007396?style=flat-square&logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Build](https://img.shields.io/badge/build-Maven-C71A36?style=flat-square&logo=apachemaven&logoColor=white)](#-building)

**English** · [Русский](README.ru.md)

</div>

---

## ✨ What it does

EnumDevelopment is an administrator/developer utility that lets you grep and refactor your server's
configuration files **without leaving the game**. Everything runs asynchronously, reports progress,
can be exported to YAML, and — for replacements — can be rolled back with a single command.

| | |
|---|---|
| 🔍 **Text search** | Find any word, phrase or JSON fragment across a folder tree |
| ♻️ **Text replace** | Rewrite matches in place, with a backup taken before every write |
| 🌈 **Color-blind matching** | Match through gradients and color codes of any format — and keep the gradient on replace |
| 🎒 **Item search** | Find the item in your main hand inside YAML sections, lists and Base64 blobs |
| 🧑 **Head textures** | Match player heads by the actual skin texture, not by profile UUID or name |
| 🔁 **Item replace** | Swap the main-hand item for the off-hand one everywhere it occurs |
| ↩️ **Undo** | Restore the files touched by the latest replacement |
| 📄 **Reports** | Save full results to `plugins/EnumDevelopment/results/*.yml` |

---

## 📚 Table of contents

- [Commands](#-commands)
- [Color-insensitive search and replacement](#-color-insensitive-search-and-replacement)
- [Item search and replacement](#-item-search-and-replacement)
- [Undo](#️-undo)
- [Quote parser](#-quote-parser)
- [Search behaviour](#-search-behaviour)
- [Configuration](#️-configuration)
- [Permissions](#-permissions)
- [Building](#-building)
- [Protected build](#️-protected-build)
- [Project layout](#️-project-layout)

---

## 🎮 Commands

```text
/ed find    "word or phrase"          /plugins [-s] [-n name] [-f] [-i] [-g]
/ed find    'JSON or phrase with "'   /plugins [-s] [-n name] [-f] [-i] [-g]
/ed replace "old phrase" "new phrase" /plugins [-s] [-n name] [-f] [-i] [-g]
/ed replace 'old JSON'   'new JSON'   /plugins [-s] [-n name] [-f] [-i] [-g]
/ed finditem                          /plugins [-s] [-n name] [-f]
/ed replaceitem                       /plugins [-s] [-n name] [-f]
/ed undo
/ed reload
/ed help
```

`/enumdevelopment` works as an alias for `/ed`.

### 🚩 Flags

| Flag | Meaning |
|---|---|
| `-s` | Save the full report to a YAML file |
| `-n` / `-name` | Report file name — the `.yml` extension is added automatically |
| `-f` | Overwrite an existing report file |
| `-i` / `--ignore-case` | Ignore letter case, regardless of `settings.case-sensitive` |
| `-cs` / `--case-sensitive` | Force case-sensitive matching |
| `-g` / `--gradient` / `--colors` | Ignore color codes and gradients of any format while matching; on replace the original colors are kept and re-applied to the new text |
| `-gs` / `--strip-colors` | Same matching as `-g`, but the replaced text is written without colors |

`-i`, `-cs`, `-g` and `-gs` apply to `/ed find` and `/ed replace` only. Flag names themselves can be
changed under `flags` in `config.yml`.

---

## 🌈 Color-insensitive search and replacement

With `-g` the query and every scanned line are compared with all color codes stripped, so a single
plain-text query finds the phrase in any of these formats:

- `&x&F&F&0&0&0&0` and `§x§f§f§0§0§0§0`
- `&#RRGGBB` and `§#RRGGBB`
- `#RRGGBB`, `{#RRGGBB}`, `<#RRGGBB>`
- MiniMessage tags such as `<gradient:#ff0000:#00ff00>`, `<rainbow>`, `<red>`, `<bold>`
- legacy `&a` / `§a` codes

On `/ed replace` with `-g` the color codes found inside a match are preserved and spread over the
replacement proportionally, so the gradient survives and only the letters change:

```text
/ed replace "Привет" "Пока" /plugins -g

&#FF0000П&#FF1100р&#FF2200и&#FF3300в&#FF4400е&#FF5500т
  ->  &#FF0000П&#FF2200о&#FF3300к&#FF5500а
```

If the replacement string carries its own color codes, it is written as given and the original codes
inside the match are dropped. `-gs` always writes the replacement without any colors.

> [!NOTE]
> Item search (`/ed finditem`, `/ed replaceitem`) is not affected by these flags.

---

## 🎒 Item search and replacement

`/ed finditem` searches files for the item held in the player's **main hand**.
`/ed replaceitem` searches for the main-hand item and replaces it with the item held in the **off hand**.

Supported item storage formats:

- Bukkit/Spigot YAML `ItemStack` sections, including nested ones such as `resultItem`, `item1`, `items.0`.
- Bukkit/Spigot YAML lists with serialized entries, e.g. `Inventory.Main: - ==: org.bukkit.inventory.ItemStack`.
- Nested maps/lists inside YAML files, for plugins that store inventories as ordinary YAML collections.
- Base64 strings holding a single `ItemStack`, an `ItemStack[]`, the common size-prefixed inventory
  format, Paper `serializeAsBytes`, or BukkitUnsafe item bytes.
- String head identifiers such as `basehead-<texture>`, `head:<texture>`, `player-head=<texture>` and
  equivalent URL/hash forms.
- Custom head wrappers such as `enum-item: custom-head`, where `texture`, `profile-id`, `profile-name`,
  `profile-textures` and the nested `item` are stored separately.
- Modern 1.20.5+ / 1.21.x Bukkit/Paper maps using `DataVersion`, `id`, `count`, `components` and
  `minecraft:profile`.
- Raw JSON/SNBT/component text containing the exact skin as Base64, a `textures.minecraft.net` URL or a
  32–64 character texture hash — even when Bukkit cannot deserialize the surrounding item map.
- Legacy skull serialization with `skull-owner` / `internal`, when the running server version can
  deserialize it.
- Unknown custom YAML serialization aliases fall back to a safe raw-file scan.

### 🧑 Player heads

Profile UUID and profile name are **ignored** while matching — the skin texture itself is compared.
Display name, lore, enchantments, PDC/custom data and the rest of the item metadata must still match.
That way the same custom skin is found even when different plugins generated different profile UUIDs.

On replace, the original texture representation is kept: Base64 stays Base64, a URL stays a URL and a
hash stays a hash.

### ⚙️ Relevant settings

```yaml
settings:
  item-search:
    match-amount: false              # ignore stack size while matching
    scan-base64: true                # decode and scan Base64 payloads
    preserve-amount-on-replace: true # keep the amount already stored in the file
    base64-min-length: 48            # minimum length treated as a Base64 payload
```

With `preserve-amount-on-replace: true` you can hold a single item in the off hand and rewrite only the
item *data*, leaving stack amounts in configs untouched. Set it to `false` to let the off-hand amount
overwrite the old one as well.

Both commands are asynchronous and accept the same report flags as text search. `replaceitem` opens an
undo session, so `/ed undo` restores the files changed by the latest item replacement.

---

## ↩️ Undo

Every successful `/ed replace` and `/ed replaceitem` takes a lightweight backup before a file is
overwritten. The latest replacement can be reverted with:

```text
/ed undo
```

Undo data lives in `plugins/EnumDevelopment/undo/`; old sessions are pruned according to
`settings.undo.max-sessions`.

---

## 💬 Quote parser

Arguments may be wrapped in double **or** single quotes. For JSON / MiniMessage-like strings that
already contain double quotes, single quotes are the comfortable choice:

```text
/ed replace '{"bold":false,"color":"white","text":"Эффективность"}' '{"bold":false,"color":"#ED78FF","text":"Эффективность"}' plugins/ -s -n enchant_replace -f
```

Escaping with `\"`, `\'` and `\\` is supported.

---

## 🔎 Search behaviour

Search and replace always run off the main thread, and progress messages are on by default.

By default only common text/config extensions are scanned, and heavy folders (caches, logs, backups,
databases) are skipped. To scan every extension:

```yaml
settings:
  allowed-extensions:
    - "*"
```

For case-insensitive text search by default (the `-i` / `-cs` flags override this per command):

```yaml
settings:
  case-sensitive: false
```

---

## ⚙️ Configuration

`config.yml` is split into `settings`, `flags` and `messages`. The most important knobs:

| Key | Default | Description |
|---|---|---|
| `max-depth` | `32` | Maximum directory recursion depth |
| `ignored-directories` | caches, logs, backups… | Folder names skipped entirely |
| `allowed-extensions` | `.yml`, `.yaml` | Extensions to scan; `"*"` means everything |
| `case-sensitive` | `true` | Default case sensitivity; overridden by `-i` / `-cs` |
| `max-chat-results` | `15` | Matches printed to chat |
| `max-stored-results` | `100000` | Matches kept in memory / written to a report |
| `max-active-tasks` | `16` | Concurrent search tasks |
| `max-file-size-kb` | `8192` | Files larger than this are skipped |
| `follow-symbolic-links` | `false` | Whether symlinks are traversed |
| `search-binary-files` | `false` | Whether binary files are scanned |
| `allow-outside-server-directory` | `false` | Guard that keeps paths inside the server root |
| `result-directory` | `results` | Report folder inside the plugin data folder |
| `charset` | `UTF-8` | Charset used to read and write files |
| `progress.*` | on, 1000 files / 3000 ms | Progress reporting cadence |
| `undo.*` | on, `undo`, 10 sessions | Undo storage folder and session retention |
| `item-search.*` | see [above](#️-relevant-settings) | Item matching and Base64 scanning |

Flag names live under `flags:` and can be renamed freely:

```yaml
flags:
  save: -s
  force: -f
  name: -n
  name-long: -name
  ignore-case: -i
  case-sensitive: -cs
  colors: -g
  strip-colors: -gs
```

All chat output lives under `messages:` and supports `&`-codes plus `&#RRGGBB` hex colours.

---

## 🔐 Permissions

| Permission | Default | Grants |
|---|---|---|
| `enumdevelopment.use` | `op` | Access to `/ed` at all |
| `enumdevelopment.find` | `op` | `/ed find` |
| `enumdevelopment.replace` | `op` | `/ed replace` |
| `enumdevelopment.finditem` | `op` | `/ed finditem` |
| `enumdevelopment.replaceitem` | `op` | `/ed replaceitem` |
| `enumdevelopment.undo` | `op` | `/ed undo` |
| `enumdevelopment.reload` | `op` | `/ed reload` |

---

## 🔨 Building

Requirements: **JDK 8+** and **Maven** on your `PATH`.

```bash
mvn clean package
```

The compiled plugin lands at:

```text
target/EnumDevelopment-1.0.6.jar
```

---

## 🛡️ Protected build

The protected build moves the plugin's real logic into `META-INF/enum.payload`: classes are renamed and
the payload is encrypted with AES-256-GCM. Only `com.enumdev.enumdevelopment.Main` and its loader
classes stay visible in the plain JAR.

```powershell
.\build-protected.ps1
```

`build-protected.bat` is a convenience wrapper around the same script.

Requirements: **Maven** and **JDK 17+** (`java` and `javac` from the same installation) for the packaging
tool — ASM 9.7.1 is fetched automatically through Maven. The resulting plugin still targets Java 8
bytecode and API 1.16.

> [!WARNING]
> `target/EnumDevelopment-<version>-mapping.txt` deobfuscates the protected build.
> Keep it private and never commit it.

---

## 🗂️ Project layout

```text
src/main/java/com/enumdev/enumdevelopment/
├── Main.java              # plugin bootstrap and payload loader
├── commands/              # /ed executor, tab completion, registration
├── config/                # config.yml access layer
├── internal/              # runtime entrypoint used by the protected build
├── managers/              # search tasks, result storage, undo sessions
├── models/                # search options, results, reports, undo records
├── services/              # file search engine and text matching
│   └── item/              # item matching, Base64 codecs, head textures
└── utils/                 # argument parser, paths, colour codes, messages

src/main/resources/        # plugin.yml, config.yml
tools/                     # obfuscator and payload protector (protected build)
build-protected.ps1/.bat   # protected build pipeline
```

---

<div align="center">

**EnumDevelopment** · Bukkit/Spigot API 1.16 · author **Jasper**

</div>
