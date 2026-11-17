# SurvivalOps serve-emu — Intel, Gathering en Beast Hunting (2026-11-13)

Deze correctieronde bouwt voort op `serve-emu-core-qa-20261113.md`. Alleen reguliere taken worden aangepast; geen tijdelijke game-events, FC, premium aankopen of extra paid recruitment.

## Bewezen uit echte beeldgegevens

- Intel `A Hero's Journey`: in diagnostiek van 2026-11-11 blijft de blauwe `Explore` (10 stamina) knop zichtbaar terwijl bestaande herkenning faalde. Nieuwe uit het foutframe uitgesneden template `SERVE_EMU_INTEL_JOURNEY_EXPLORE` herkent de doelknop met 100% in de betreffende masked replay; een negatief ander scherm matcht niet.
- Intel `Rescue Survivors`: het groene `Rescue` (12 stamina) scherm is uit een diagnostiek van 2026-11-11 vastgelegd en als `SERVE_EMU_INTEL_SURVIVOR_RESCUE` gereplayd; positieve score 100%, negatief scherm geen hit.
- Intel wacht nu op de huidige én oudere knopweergave. Na een tap wordt niet geconcludeerd dat de opdracht is begonnen als alleen een oude knopvariant verdwenen is.
- Gathering gebruikt de modernere live `SERVE_EMU_GATHER_DEPLOY` herkenning uit de hoofdbranch, maar telt een march alleen na terugkeer naar een positief herkende World-homeanker. Enkel het verdwijnen van `Deploy` is onvoldoende.
- Beast Hunting tikt alleen een in twee frames bevestigde Deploy en boekt alleen stamina/statistieken bij geverifieerde World-terugkeer.
- DeploymentHelper accepteert huidige serve-emu World-anker en de oude BlueStacks World-variant. Geen dubbel Deploy als een transition onduidelijk is.

## Uitgevoerde controles

- `./mvnw -q -pl modules/tasks -am test` geslaagd op de geïntegreerde branch.
- `./mvnw -q -DskipTests package` geslaagd op de geïntegreerde branch.
- De nieuwe maskerbestanden bevatten alleen acties/knoppen; persoonlijke gamegegevens zijn niet onderdeel van de tests.

## Grenzen

- De opgeslagen Intel-opnamen bewijzen correcte herkenning van **die** frames, niet dat Rescue en Journey live op dit low-level TrueNAS-account al beschikbaar zijn en geheel automatisch doorlopen.
- Beast Hunting is hier niet daadwerkelijk gestart, om ongevraagd stamina en een mars te vermijden. De wijziging verifieert de postcondition wanneer de functie op een gerechtigde taak wordt uitgevoerd.
- De core-audit documenteert wel dat de TrueNAS-game daadwerkelijk Arena, Training en Gathering-acties accepteerde; dat is nog niet hetzelfde als een volledige Windows-MSI end-to-end-run.
- Een herhaaltest via de gebruiker zijn Windows-exemplaar en de LAN-diagnostiek blijft nodig om de echte betrouwbaarheid over meerdere rondes vast te stellen. Bij onzekerheid stopt de routine zonder nogmaals te klikken.
