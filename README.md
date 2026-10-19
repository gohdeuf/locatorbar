# Locator Triangulator (Fabric, Minecraft 26.3)

Client-Mod, die die seit 1.21.6 existierende **Locator Bar** (`/gamerule locator_bar`) nutzt, um die Positionen anderer Mitspieler per **Triangulation** zu schätzen.

## 🚀 Funktionsweise

1. **Datenfang:** Der Server schickt für sichtbare Mitspieler **Waypoint-Pakete** mit einem **Azimuth** (absoluter Peilwinkel in Radiant) – aber keine direkte Position.
2. **Abfangen:** Ein Mixin (`ClientPacketListenerMixin`) fängt diese Pakete ab und speichert pro Spieler einen Messwert im `WaypointStore`: eigene Position zum Messzeitpunkt + Peilwinkel + Zeitstempel. Jüngere Messungen werden stärker gewichtet, alte verworfen.
3. **Die Mathematik:** Der `LeastSquaresTriangulator` berechnet aus ≥ 2 Messungen (idealerweise von weit auseinanderliegenden Standpunkten) den wahrscheinlichsten Schnittpunkt der Peilstrahlen per linearer Ausgleichsrechnung. Es ist kein Iterieren oder Startwert nötig, da die Strahl-Residuen linear in der gesuchten Position sind. Zudem wird die Kondition geprüft und ein RMS-Restfehler ausgegeben.
4. **Anzeige:** Das HUD zeigt pro Ziel: Name, geschätzte X/Z-Koordinaten, Distanz und Restunsicherheit (RMS). Eine Zeile darunter gibt Hinweise, ob die Peilwinkel bereits für eine Schätzung ausreichen oder ob man sich für eine bessere Triangulation weiter seitlich bewegen sollte (Baseline vergrößern). Wenn Minecraft für nahe Spieler stattdessen eine direkte Position oder eine ungefähre Chunk-Position sendet, zeigt das HUD diese ebenfalls an. Spieler-UUIDs werden aufgelöst, sobald der Client den zugehörigen Spielernamen kennt.

## 🕹️ Bedienung & Befehle

Mit **J** (änderbar unter *Optionen → Steuerung → Tastenbelegung → Sonstiges*)
kann die Spieler-Ortung ein- und ausgeblendet werden. Das HUD erscheint links
oben in einem abgesetzten Panel.

Folgende Ingame-Befehle stehen zur Verfügung:
* `/triangulator status` – Zeigt den aktuellen Zustand der Verfolgung und die Anzahl der gesammelten Samples.
* `/triangulator clear` – Löscht alle aktuell gespeicherten Wegpunkte und Tracking-Daten.
* `/triangulator forget <id>` – Verwirft die Daten für ein spezifisches Ziel.
* `/triangulator lizenz` – Gibt den GPLv3-Lizenztext seitenweise im Chat aus; mit `/triangulator lizenz <seite>` oder den anklickbaren Zurück/Weiter-Schaltflächen blättern.

## 📂 Aufbau

src/main/java/de/example/triangulator/
├── TriangulatorMod.java            # Einstieg (ClientModInitializer)
├── config/TriConfig.java           # Einfache JSON-Config + Keybind (HUD an/aus)
├── waypoint/TrackedTarget.java     # Pro beobachtetem Spieler: Farbe, Samples, Schätzung
├── waypoint/WaypointStore.java     # Thread-sichere Zentrale aller Ziele
├── triangulation/BearingSample.java
├── triangulation/LeastSquaresTriangulator.java   # Lineares 2x2-Gleichungssystem (Projektionsmatrix A = I − d·dᵀ)
├── triangulation/TriangulationService.java       # Wertet Pakete aus, steuert Neuberechnung
├── command/TriangulatorCommands.java             # Registrierung der Chat-Befehle
├── render/TriangulationHud.java    # HUD-Rendering
└── mixin/ClientPacketListenerMixin.java          # Einziger Mixin für Paket-Intercepts

## 🛠️ Setup

> 💡 **Wichtig:** Kein `mappings`-Eintrag in der `build.gradle` nötig! Seit Minecraft 26.1+ ist das Spiel standardmäßig **unobfuscated** – es gibt keine Mojmap-Artifacts mehr. Die Klassennamen sind direkt im Vanilla-Jar lesbar.

**Empfohlen:** Erstelle ein leeres Projekt-Skelett mit dem offiziellen [Fabric Template Generator](https://fabricmc.net/develop/template) (Minecraft 26.3, Mojang Mappings) und kopiere die Quellcodedateien aus diesem Projekt darüber.

Alternativ kannst du die Toolchain direkt via Terminal initialisieren:
```bash
gradle wrapper --gradle-version 9.2   # Gradle mit Java 25 im JAVA_HOME aufsetzen
./gradlew genSources
./gradlew runClient
```

## 💻 Minecraft-26.3-API Details

Der Client-Mixin fängt `ClientboundTrackedWaypointPacket` in `ClientPacketListener.handleWaypoint` ab. Paketoperation, Waypoint-ID und Icon werden über ihre typisierten Accessoren verarbeitet. 

Da Minecraft den Azimuth-Winkel nur auf einem nicht-öffentlichen Waypoint-Untertyp bereitstellt, wird das `angle`-Feld an dieser Stelle reflektiv ausgelesen. Die HUD-Anzeige wird sauber über das Fabric-API `HudElementRegistry` registriert.

## ⚖️ Fairplay-Hinweis

Die Mod nutzt ausschließlich Daten, die der Vanilla-Server ohnehin an alle Clients sendet. Auf vielen öffentlichen Mehrspieler-Servern (insbesondere im kompetitiven PvP) ist das Nutzen automatisierter Positionstools und Tracker dennoch gegen das Regelwerk. Bitte verwende diese Mod nur auf deinen eigenen Servern oder wenn du die ausdrückliche Erlaubnis der Administration hast.