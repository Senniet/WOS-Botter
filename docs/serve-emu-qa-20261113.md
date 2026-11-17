# SurvivalOps / serve-emu: 2026-11-13 QA en herkenningsaudit

Doel: bestaande dagelijkse taken betrouwbaarder laten werken bij 720x1280 portrait / 320 DPI, zonder aankopen, FC-upgrades of tests van beëindigde events. De bewering dat **alle** routines werken of dat herkenning 100% is, volgt niet uit deze audit.

## Live gecontroleerd met TrueNAS ADB

- Emulator: `WhiteoutSurvival_Main` via extern TCP-ADB-adres `192.168.1.20:15559`; Whiteout Survival draaide.
- City en World: actuele (respectievelijk blauwe World- en bruine City-)knoppen bekeken, beide icoonvarianten afzonderlijk opgenomen.
- Exploration: geopend via het onderste menu; buitenste Claim leidde naar Idle Income; gratis binnenste Claim leidde naar de Rewards-overlay. Geen valuta besteed. De daarbij gebruikte screenshots voor tests zijn gemaskeerd tot uitsluitend UI-elementen.
- Daily Missions: het Chapter-venster en de Daily-tab geopend; titels en tabpositie opgenomen. Geen betaald(e) acties uitgevoerd.
- Mail: het Mail-venster en System-tab gecontroleerd; mailtitel, Read & Claim All en ongelezen-markeringen herkend. Geen mailboxacties noodzakelijk voor de herkenningstest.
- Chief Stamina: vastgesteld dat de groene stamina-HUD op `x=8..86,y=163..193` staat. De vorige code tikte in het Chief-profiel. Het gratis/premium keuzescherm en de fractionele waarde `484/200` zijn met OCR getest. Het oranje `Buy & Use`-element wordt expliciet als onveilig herkend. Geen aankopen.
- Sidebar: geopende Daily-sectie uit de actuele emulator gemeten; dit scherm gebruikt lager gepositioneerde tabbladen dan eerdere versies.
- Header-Back en Rewards-overlay uit echte opnamen herkend; geen blinde Android Back bij bekende rootstatus.

## Verbeteringen

- City/World/Back-verificatie ondersteunt oude templates plus de actuele UI, met een nieuwe framecontrole vóór navigatie.
- Sidebar ondersteunt beide tab-hoogtes; snellere ingestelde wachttijden met bounded polling en controle op schermwisselingen.
- Daily-tab en titel ondersteunen huidige renderings; lange vaste wacht vervangen door korte gecontroleerde wachttijd.
- Exploration/Idle Income herkent actuele Rewards-tekst, vermijdt ingangen wanneer het scherm al openstaat en sluit een geverifieerd scherm via de zichtbare pijl.
- Mail herkent actuele titel en voetknop, gebruikt juiste tabposities, voert hoogstens één geverifieerde Read & Claim All per ronde uit en sluit via de X.
- Stamina-HUD en OCR volgen de waargenomen coördinaten; oude OCR-gebied blijft fallback, gem-aankoop via `Buy & Use` is afgeschermd.

## Veiligheidsgrenzen

- Geen premiumvaluta, winkelbestellingen, FC, tijdelijke events of andere onomkeerbare acties in live tests.
- Geen in-game berichten verzonden, accounts gewisseld, troepen uitgezonden of betaald aas gebruikt.
- Regressiebeelden bevatten uitsluitend gemaskeerde interface-elementen; geen Google-inloggegevens of gebruikers-ID's.
- Windows-MSI moet na GitHub-publicatie op het daadwerkelijke Windows/TrueNAS-ADB-pad worden gecontroleerd; een Java-test of een handmatige ADB-tap is geen volledige end-to-end-validatie van alle routinestappen.
- De rest van de vele routinetypes (zoals Training, Intel, Gathering, Research en Arena) is niet afzonderlijk live uitgevoerd. Nieuwe logs blijven nodig om varianten van deze schermen gecontroleerd te beoordelen.
