# EnumDevelopment

Bukkit/Spigot plugin for API 1.16. Author: Jasper.

## Commands

```text
/ed find "word or phrase" /plugins [-s] [-n name] [-f]
/ed find 'JSON or phrase with double quotes' /plugins [-s] [-n name] [-f]
/ed replace "old word or phrase" "new word or phrase" /plugins [-s] [-n name] [-f]
/ed replace 'old JSON' 'new JSON' /plugins [-s] [-n name] [-f]
/ed finditem /plugins [-s] [-n name] [-f]
/ed replaceitem /plugins [-s] [-n name] [-f]
/ed undo
/ed reload
```

Flags:

- `-s` — save report to YAML.
- `-n` / `-name` — report file name. The `.yml` extension is added automatically.
- `-f` — overwrite an existing report file.

## Item search and replacement

`/ed finditem` searches files for the item held in the player's main hand.

`/ed replaceitem` searches for the item held in the main hand and replaces it with the item held in the off hand.

Supported item storage formats:

- Bukkit/Spigot YAML `ItemStack` sections, including nested sections such as `resultItem`, `item1`, `items.0`, etc.
- Bukkit/Spigot YAML lists with serialized entries, for example `Inventory.Main: - ==: org.bukkit.inventory.ItemStack`.
- Nested maps/lists inside YAML files when a plugin stores inventories as ordinary YAML collections.
- Base64 strings containing a single `ItemStack`, an `ItemStack[]`, or the common size-prefixed inventory format.

The command is asynchronous and uses the same report flags as text search. `replaceitem` creates an undo session, so `/ed undo` restores the files that were changed by the latest item replacement.

Relevant settings:

```yaml
settings:
  item-search:
    match-amount: false
    scan-base64: true
    preserve-amount-on-replace: true
    base64-min-length: 48
```

`match-amount: false` means the amount in the stack is ignored while matching.

`preserve-amount-on-replace: true` means `/ed replaceitem` keeps the old amount from the matched item in the file. You can hold one item in the off hand and replace only the item data, while stack amounts in configs remain unchanged. Set it to `false` if the amount from the off hand must overwrite the old amount too.

## Undo

Every successful `/ed replace` and `/ed replaceitem` creates a lightweight file backup before the original file is overwritten. The latest replacement can be reverted with:

```text
/ed undo
```

Undo data is stored in `plugins/EnumDevelopment/undo/` and old sessions are cleaned according to `settings.undo.max-sessions`.

## Quote parser

Arguments can be wrapped in either double quotes or single quotes. For JSON/minimessage-like strings that already contain double quotes, single quotes are recommended:

```text
/ed replace '{"bold":false,"color":"white","text":"Эффективность"}' '{"bold":false,"color":"#ED78FF","text":"Эффективность"}' plugins/ -s -n enchant_replace -f
```

The parser also supports escaping with `\"`, `\'` and `\\`.

## Search notes

Search and replace are asynchronous. Progress messages are enabled by default.

By default the plugin scans only common text/config extensions and skips heavy folders such as caches, logs, backups and databases. To scan all extensions, set:

```yaml
settings:
  allowed-extensions:
    - "*"
```

For case-insensitive text search:

```yaml
settings:
  case-sensitive: false
```

## Build

```bash
mvn clean package
```

The compiled jar will be created at:

```text
target/EnumDevelopment.jar
```

## Защищённая сборка 1.0.5

Основная логика после сборки хранится в `META-INF/enum.payload`, предварительно переименовывается и шифруется AES-256-GCM. В открытом JAR остаётся только `com.enumdev.enumdevelopment.Main` и его служебные классы загрузчика.

Сборка на Windows:

```powershell
.\build-protected.ps1
```

Требуются Maven и JDK 17+ для работы инструмента упаковки. Итоговый плагин сохраняет Java 8 bytecode и API 1.16. Mapping-файл из `target` следует хранить приватно.
