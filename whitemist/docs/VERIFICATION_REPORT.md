# Отчёт автоматической верификации

Сгенерирован `tools/verify.py`. **Это статическая проверка, а не сборка:** Gradle и Minecraft в моей среде запустить нельзя (нет доступа к maven.fabricmc.net и серверам Mojang).

**Итого:** PASS=87, WARN=2, FAIL=0

## WARN

- `api-method`: method 'moveTo(' never appears in the 26.3 corpora (rename risk): src/main/java/com/whitemist/entity/ListenerEntity.java:154
- `api-method`: method 'withSuppressedOutput(' never appears in the 26.3 corpora (rename risk): src/main/java/com/whitemist/mist/ClockBridge.java:31

## INFO

- `corpus`: indexed 4313 files from fabric-api, neoforge, fabric-docs/reference/latest/src; 4643 distinct imports, 1702 Fabric classes, 823 patched vanilla classes
- `api-import`: 174 import statements checked, 0 unconfirmed

## PASS (по областям)

- **build**: 6 проверок
- **clock**: 3 проверок
- **imports**: 22 проверок
- **json**: 7 проверок
- **lang**: 3 проверок
- **legacy**: 1 проверок
- **mod.json**: 6 проверок
- **selftest**: 1 проверок
- **sound**: 4 проверок
- **syntax**: 23 проверок
- **tag**: 2 проверок
- **texture**: 3 проверок
- **timeline**: 6 проверок
