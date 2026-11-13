# serve-emu (TrueNAS) verbinden met SurvivalOps

Vanaf SurvivalOps v1.1.32 kan SurvivalOps Android-telefoons aansturen die buiten Windows draaien, bijvoorbeeld via de TrueNAS Custom App **serve-emu**.

1. Start de Android-emulator in serve-emu op TrueNAS en zorg dat de ADB TCP-poort voor de Windows-pc bereikbaar is. Gebruik alleen een vertrouwd LAN/VPN; stel ADB niet publiek bloot.
2. Kies in SurvivalOps **Emulator settings** de nieuwe emulator **serve-emu (TrueNAS / remote ADB)**.
3. Klik bij deze emulator op **...** en voer het ADB-adres in als `host:port`, bijvoorbeeld `192.168.1.20:5555` **als dit de werkelijke gepubliceerde ADB-poort is**. Voeg voor meerdere telefoons adressen toe gescheiden door komma's, in de volgorde van de profielen: `host:5555,host:5557`.
4. Selecteer het radioknopje om serve-emu actief te maken, sla de configuratie op en start de taken. Telefoonindex `0` verwijst naar het eerste endpoint, `1` naar het tweede, enz.

### Controle vanaf Windows

Gebruik de met SurvivalOps meegeleverde ADB-client in `tools\\adb\\adb.exe` (of je eigen `adb.exe`):

```powershell
adb connect 192.168.1.20:5555
adb devices
adb -s 192.168.1.20:5555 shell getprop ro.build.version.release
```

Vervang `192.168.1.20:5555` door de daadwerkelijke ADB-portmapping uit serve-emu. Als `adb connect` faalt, controleer eerst de TrueNAS-app en firewall. Een webconsolepoort is niet hetzelfde als een ADB-poort.

SurvivalOps detecteert de verbonden apparaten via ADB, gebruikt dezelfde screenshot-/input-commando's als voor BlueStacks, en normaliseert een staand 9:16-scherm naar de bestaande 720x1280-herkenningscoördinaten. De automatisering start of stopt de TrueNAS-app zelf **niet**. Android/ADB-connectiviteit alleen garandeert nog niet dat Whiteout Survival geïnstalleerd of de schermresolutie geschikt is.
