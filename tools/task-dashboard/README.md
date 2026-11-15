# SurvivalOps — gedeeld QA-dashboard

Dit dashboard draait als zelfstandige **HTTPS**-dienst op de webserver. Het leest de oorspronkelijke SurvivalOps-diagnostiek uitsluitend en maakt geen gameklikken of aankopen.

## Openen

- Browser op eigen LAN: **https://10.0.0.100:9097**
- Privaat gegenereerd wachtwoord: `~/.config/survivalops-qa-dashboard/initial-password.txt`
- Service: `systemctl --user status survivalops-qa-dashboard`
- Applicatie: `/home/michel/survivalops-qa-dashboard/`
- Database: `~/.local/share/survivalops-qa/dashboard.sqlite3`
- Broncode: `tools/task-dashboard/` in WOS-Botter
- Screenshot- en foutbron: `~/survivalops-diagnostics/inbox/`

Het certificaat is self-signed met een SAN voor 10.0.0.100. De browser kan de eerste keer een beveiligingsuitzondering vragen; toegang is alleen bedoeld voor het eigen LAN.

## Werkwijze

Het dashboard importeert alle 61 taken (inclusief Custom Task) uit de Java-taakcatalogus. De persoonlijke statussen, notities en handmatig geregistreerde problemen staan permanent in SQLite (WAL). Tijdens synchronisatie worden diagnoses uit het bestaande JSON-inboxarchief ingelezen. Bijpassende meldingen en screenshots verschijnen bij de taak. De bronbestanden, uploader en uploadtoken worden niet gewijzigd.

De statussen zijn:
- `not_tested` — Nog niet getest
- `testing` — In onderzoek
- `working` — Lijkt te werken (niet volledig geverifieerd)
- `broken` — Werkt niet
- `blocked` — Geblokkeerd door spelstatus of afhankelijkheid
- `verified` — Afgevinkt als volledig werkend na daadwerkelijke live-test

De gebruiker kan taken afvinken, notities opslaan, nieuwe fouten toevoegen, fouten sluiten/heropenen en de diagnostiek verversen. Iedere wijziging krijgt een actor en tijdstip. De per-taak versieteller voorkomt dat twee gebruikers elkaars wijzigingen onopgemerkt overschrijven.

## Live diagnostiek — debug en screenshots

Via **Live diagnostiek** boven in het dashboard zie je de inkomende
SurvivalOps-meldingen als tijdlijn. De pagina controleert elke **8 seconden**
via een geauthenticeerde HTTPS-aanvraag op nieuwe uploads (geen achtergrond-
schermstream van de emulator). Er zijn filters voor taak, systeemmeldingen,
foutniveau, trefwoord en periode (24 uur / 3 / 7 / 14 dagen / volledig archief).
De knop **Pauzeren** zet automatische verversing uit; de taakstatussen en
notities blijven onaangetast.

Een klik op een melding toont het tijdstip, taak of systeem, ernst, platform,
bijbehorende screenshot met vergroting en de werkelijk meegezonden debuglogregels
(INFO/WARN/ERROR/DEBUG wanneer aanwezig). OCR-scores, klikroutes en redenen
voor herpogingen worden zichtbaar als SurvivalOps die in `log_tail` heeft opgenomen.
De upload bevat doorgaans een *fragment* van recente logs, niet het volledige
proceslog; deze regels kunnen daarom ook eerdere taken betreffen.

De pagina toont expliciet de leeftijd van de **laatst ontvangen** upload en
waarschuwt als er langer dan twee minuten geen nieuwe gegevens zijn. Dat is geen
bewijs dat de game gestopt is; alleen dat er geen diagnostische uploads binnenkomen.
Wanneer de verzendende Windows-app geen nieuwe meldingen produceert, blijft het
bestaande archief beschikbaar. Alleen nieuwe bestanden in de oorspronkelijke
LAN-inbox worden in de dashboarddatabase geïndexeerd; het dashboard schrijft
geen wijzigingen terug naar de diagnostische bron.

APIs, uitsluitend met dezelfde HTTPS-sessiecookies:
- `GET /api/diagnostics?task=all&severity=error&hours=24&offset=0&limit=40`
- `GET /api/diagnostics/detail?path=YYYY-MM-DD/incident.json`
- `GET /api/diagnostic/image?path=YYYY-MM-DD/incident.json`

De lijst bevat alleen metadata; het logfragment en de afbeelding worden pas
bij openen van een individuele diagnose opgevraagd. In gelogde toegangscodes
worden wachtwoorden/tokens afgeschermd vóór verzending naar de browser.
De data en account-screenshots zijn privé: deel geen openbare dashboardlinks,
logs of screenshots met onbedoelde derden.

**Automatische screenshots en unittests zetten nooit een taak op VERIFIED.** Een live-eindcontrole moet de actie, de resulterende spelstatus en de volgende planning bevestigen.

## Dezelfde database vanuit ChatGPT

Bij de opdracht ‘bekijk het dashboard en ga verder’ gebruikt de assistent via SSH:

```bash
cd /home/michel/survivalops-qa-dashboard
python3 qa_ctl.py summary
python3 qa_ctl.py list --status broken
python3 qa_ctl.py show STOREHOUSE_CHEST
python3 qa_ctl.py mark STOREHOUSE_CHEST testing --notes "Herkenning in onderzoek"
python3 qa_ctl.py issue STOREHOUSE_CHEST "Claim niet bevestigd" --detail "Na klik blijft dialoog staan"
python3 qa_ctl.py resolve 1
```

De CLI schrijft dezelfde records als de webpagina, met actor ‘ChatGPT’. Voor een CLI-status VERIFIED is expliciete live-testbewijsnotitie verplicht. Gebruik geen tweede losse Markdown-checklist als leidende voortgang.

## Beheer

```bash
systemctl --user status survivalops-qa-dashboard
systemctl --user restart survivalops-qa-dashboard
journalctl --user -u survivalops-qa-dashboard -n 40 --no-pager
python3 /home/michel/survivalops-qa-dashboard/qa_ctl.py summary
```

Bij broncodewijzigingen: voer `python3 tools/task-dashboard/export_catalog.py` uit in de repository, kopieer alleen de scripts/HTML/CSS/JS en catalog.json naar de applicatiedirectory en herstart de user-service. **Overschrijf nooit de SQLite-database of het auth.json-bestand.**

## Tests

```bash
cd tools/task-dashboard
python3 -m unittest discover -s . -p 'test_*.py' -v
node --check static/app.js
```

De HTTP-API vereist authenticatie, server-side gesigneerde cookies en HTTPS. De server weigert cross-origin schrijfacties en gebruikt invoervalidatie, een login-rate-limit en parametrische SQL. Wachtwoord en session key blijven buiten Git; het diagnostische uploadtoken wordt niet hergebruikt.
