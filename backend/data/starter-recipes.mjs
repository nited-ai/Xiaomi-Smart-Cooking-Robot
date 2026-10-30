// Authored Kalomatik adaptations. Source links identify inspiration, not copied
// Cookidoo instructions, licensed photographs, or a successful cooking trial.
const manual=(kind,title,instruction,uses=[],seconds=null)=>({kind,title,instruction,ingredientUses:uses.map(value=>typeof value==='string'?{ingredientId:value,share:1}:{ingredientId:value[0],share:value[1]}),settings:{durationSeconds:seconds,temperatureC:null,speedLevel:null,direction:null}});
const prepare=(title,text,uses=[])=>manual('prepare',title,text,uses);
const weigh=(title,text,uses)=>manual('weigh',title,text,uses);
const rest=(title,text,seconds)=>manual('rest',title,text,[],seconds);
const finish=(title,text,uses=[])=>manual('finish',title,text,uses);
const machine=(title,text,seconds,temp,speed,direction='clockwise')=>({kind:'machine',title,instruction:text,ingredientUses:[],settings:{durationSeconds:seconds,temperatureC:temp,speedLevel:speed,direction}});
const ingredient=(id,name,amount,unit='g')=>({id,name,amount,unit});
const recipe=(slug,title,reference,ingredients,tools,steps,timing,tips=[])=>({slug,title,servings:4,reference,ingredients,tools,steps,timing,tips});

export const starterRecipes=[
  recipe('crepes','Crêpes',{
    title:'Cookidoo: Crêpes',url:'https://cookidoo.thermomix.com/recipes/recipe/en-US/r439938',access:'public-ingredients-only',
    adaptation:'Eigenes Mengenverhältnis und eigene Zubereitung; die geschützte Cookidoo-Anleitung wurde nicht abgerufen.'
  },[
    ingredient('milk','Milch',500),ingredient('flour','Weizenmehl Type 405',250),ingredient('eggs','Eier',2,'Stück'),ingredient('butter','Butter',40),ingredient('salt','Salz',1)
  ],['mixing-blade','spatula','scale','pan'],[
    weigh('Butter einwiegen','Drei Viertel der Butter in kleinen Stücken in den Topf einwiegen. Den Rest bereitstellen.',[['butter',0.75]]),
    machine('Butter schmelzen','Butter schmelzen lassen. Anschließend bei Stillstand prüfen; verbliebene weiche Stücke mit dem Spatel zerdrücken.',180,50,1),
    weigh('Milch einwiegen','Milch in den Topf einwiegen.',['milk']),prepare('Eier und Salz zugeben','Eier einzeln in einer Schale aufschlagen, Schalen entfernen und mit dem Salz in den Topf geben.',['eggs','salt']),
    weigh('Mehl einwiegen','Mehl gleichmäßig in den Topf einwiegen.',['flour']),machine('Teig verrühren','Teig verrühren. Bei Stillstand die Topfwand mit dem Spatel abstreifen; sichtbare Mehlklümpchen von Hand mit einem Schneebesen auflösen.',60,0,3),
    rest('Teig ruhen lassen','Teig in eine Schüssel umfüllen und abgedeckt 30 Minuten ruhen lassen.',1800),
    prepare('Pfanne vorbereiten','Eine beschichtete Pfanne auf dem Herd bei mittlerer Hitze erwärmen. Restliche Butter dünn zum Ausstreichen verwenden.',['butter'].map(id=>[id,0.25])),
    finish('Crêpes ausbacken','Jeweils eine dünne Teigschicht in der Pfanne verteilen. Etwa 1–2 Minuten backen, bis die Oberfläche fest und der Rand leicht gebräunt ist; wenden und 30–60 Sekunden fertigbacken. Ergibt etwa 8–10 Crêpes für 4 Portionen.')
  ],{prepMinutes:15,cookMinutes:25,restMinutes:30},['Milch, Eier und Weizen enthalten. Pfannenhitze nach der ersten Crêpe anpassen.']),

  recipe('brownies','Schokoladen-Brownies',{
    title:'Cookidoo: Brownies',url:'https://cookidoo.thermomix.com/recipes/recipe/en-US/r168965',access:'public-ingredients-only',
    adaptation:'Eigene kleinere Backmenge ohne Nüsse und eigene Zubereitung; kein Originalimport.'
  },[
    ingredient('chocolate','Zartbitterschokolade',150),ingredient('butter','Butter',100),ingredient('sugar','Zucker',120),ingredient('eggs','Eier',2,'Stück'),ingredient('flour','Weizenmehl Type 405',70),ingredient('cocoa','Backkakao',15),ingredient('salt','Salz',1)
  ],['mixing-blade','spatula','scale','oven'],[
    prepare('Backform vorbereiten','Eine quadratische, etwa 20 × 20 cm große Backform mit Rand (kein flaches Backblech) mit Backpapier auslegen. Backofen von Hand auf 175 °C Ober-/Unterhitze vorheizen.'),
    prepare('Schokolade vorbereiten','Schokolade entlang der Rillen in etwa 2–3 cm große Stücke teilen und in den Topf geben.',['chocolate']),
    machine('Schokolade zerkleinern','Die kalten Schokoladenstücke mit dem Messer zerkleinern. Bei Stillstand prüfen: Es sollen kleine Flocken entstehen; grobe Stücke mit dem Spatel nach unten schieben und bei Bedarf noch kurz nacharbeiten.',6,0,6),
    weigh('Butter einwiegen','Butter in etwa 1–2 cm großen Würfeln zur zerkleinerten Schokolade in den Topf einwiegen.',['butter']),
    machine('Schokolade schmelzen','Butter und die zerkleinerte Schokolade mit Rühren bei geschlossenem Deckel schmelzen.',240,50,1,'counterclockwise'),
    prepare('Schmelze prüfen','Stillstand abwarten und mit dem Spatel glatt rühren. Erst fortfahren, wenn keine festen Schokoladenstücke mehr sichtbar sind.'),
    rest('Abkühlen lassen','Schokoladenmasse bei ausgeschalteter Maschine 5 Minuten abkühlen lassen; sie soll höchstens handwarm sein, bevor Eier hinzukommen.',300),
    weigh('Zucker einwiegen','Zucker in den Topf einwiegen.',['sugar']),prepare('Eier zugeben','Eier einzeln in einer Schale aufschlagen und ohne Schalenreste zur handwarmen Schokoladenmasse in den Topf geben.',['eggs']),
    machine('Eier einarbeiten','Die bereits zugegebenen Eier mit der Einstellung Rühren kurz in die höchstens handwarme Schokoladenmasse einarbeiten. Bei Stillstand prüfen: Die Masse soll gleichmäßig sein, nicht luftig aufgeschlagen.',45,0,2,'counterclockwise'),weigh('Trockene Zutaten einwiegen','Mehl, Backkakao und Salz nacheinander direkt zur Masse in den Topf einwiegen.',['flour','cocoa','salt']),
    machine('Teig kurz verrühren','Mit Rühren nur kurz vermischen; anschließend bei Stillstand verbliebene Mehlspuren mit dem Spatel unterheben.',20,0,1,'counterclockwise'),
    finish('Brownies backen','Teig in die vorbereitete Form füllen. Im vorgeheizten Backofen bei 175 °C Ober-/Unterhitze etwa 22–28 Minuten backen. Die Oberfläche ist fest, ein Holzstäbchen zeigt feuchte Krümel, aber keinen flüssigen Teig.'),
    rest('Abkühlen lassen','In der Form mindestens 30 Minuten abkühlen lassen, dann in 8 kleine Stücke schneiden: 2 Stücke je Portion.',1800)
  ],{prepMinutes:20,cookMinutes:28,restMinutes:35},['Enthält Milch, Eier und Weizen. Ofenzeiten hängen von der Form ab.']),

  recipe('focaccia','Rosmarin-Focaccia',{
    title:'Cookidoo: Focaccia',url:'https://cookidoo.thermomix.com/recipes/recipe/en-US/r80558',access:'public-ingredients-only',
    adaptation:'Eigene Fassung mit Falten von Hand. Kein unbestätigtes Knetprogramm der C3.'
  },[
    ingredient('water','Wasser',300),ingredient('yeast','Frische Hefe',10),ingredient('sugar','Zucker',5),ingredient('flour','Weizenmehl Type 550',400),ingredient('salt','Salz',8),ingredient('oil','Olivenöl',40),ingredient('rosemary','Rosmarinnadeln',3),ingredient('flaky','Grobes Meersalz',2)
  ],['mixing-blade','spatula','scale','oven'],[
    weigh('Wasser einwiegen','Wasser in den Topf einwiegen.',['water']),prepare('Hefe und Zucker zugeben','Hefe hineinbröckeln und Zucker zugeben.',['yeast','sugar']),
    machine('Hefe auflösen','Hefe in der Flüssigkeit auflösen.',120,35,1),
    prepare('Teig von Hand mischen','Hefewasser in eine große Schüssel gießen. Mehl, feines Salz und die Hälfte des Olivenöls zugeben. Mit einem stabilen Löffel verrühren, bis kein trockenes Mehl übrig ist. Der Teig bleibt weich; kein Maschinen-Knetprogramm verwenden.',['flour','salt',['oil',0.5]]),
    rest('Erste Teigruhe','Schüssel abdecken und 30 Minuten bei Raumtemperatur stehen lassen.',1800),
    prepare('Erstes Falten','Mit einem Teigschaber den Teig von vier Seiten jeweils zur Mitte ziehen und falten.'),rest('Zweite Teigruhe','Abgedeckt weitere 30 Minuten ruhen lassen.',1800),
    prepare('Zweites Falten','Erneut von vier Seiten zur Mitte falten.'),rest('Aufgehen lassen','Weitere 30–60 Minuten abgedeckt gehen lassen, bis der Teig deutlich luftig ist; die eingestellte Wartezeit ist der erste Prüfzeitpunkt.',1800),
    prepare('Form ölen','Eine etwa 20 × 30 cm große Form mit einem Viertel des gesamten Olivenöls ausstreichen. Teig hineingleiten lassen und vorsichtig verteilen.',[['oil',0.25]]),
    rest('In der Form ruhen lassen','Teig abgedeckt 30 Minuten entspannen lassen.',1800),
    prepare('Backofen vorheizen','Backofen von Hand auf 220 °C Ober-/Unterhitze vorheizen.'),
    prepare('Focaccia belegen','Restliches Olivenöl auf dem Teig verteilen. Mit den Fingerspitzen Mulden eindrücken und mit Rosmarin sowie grobem Meersalz bestreuen.',[['oil',0.25],'rosemary','flaky']),
    finish('Focaccia backen','Im vorgeheizten Backofen bei 220 °C Ober-/Unterhitze etwa 25–30 Minuten goldbraun backen. Der Boden soll durchgebacken sein. Auf einem Gitter 10 Minuten abkühlen lassen und in 8 Stücke teilen; 2 Stücke je Portion.')
  ],{prepMinutes:20,cookMinutes:30,restMinutes:130},['Enthält Weizen. Teigreife nach Volumen und Luftigkeit beurteilen, nicht allein nach dem Timer.']),

  recipe('matcha-ice','Matcha-Eis',{
    title:'Cookidoo: Matcha-Eis',url:'https://cookidoo.thermomix.com/recipes/recipe/en-US/r144269',access:'public-ingredients-only',
    adaptation:'Eigene eifreie Fassung mit Gefrieren und Rühren von Hand; kein Zerkleinern gefrorener Blöcke.'
  },[
    ingredient('milk','Vollmilch',250),ingredient('cream','Sahne',250),ingredient('sugar','Zucker',80),ingredient('matcha','Matchapulver',4)
  ],['mixing-blade','spatula','scale'],[
    prepare('Gefrierbehälter bereitstellen','Einen sauberen flachen, gefriergeeigneten Behälter mit Deckel und etwa 1 Liter Volumen bereitstellen. Im Gefrierfach bei −18 °C Platz schaffen.'),
    weigh('Milch und Zucker einwiegen','Milch und Zucker in den Topf einwiegen.',['milk','sugar']),machine('Zucker auflösen','Milch und Zucker erwärmen. Bei Stillstand prüfen, dass sich der Zucker aufgelöst hat.',180,60,1),
    prepare('Matcha anrühren','Etwas warme Milch aus dem Topf in eine kleine Schale geben. Matcha hineinsieben, mit einem Schneebesen klümpchenfrei verrühren und vollständig in den Topf zurückgeben.',['matcha']),
    weigh('Sahne einwiegen','Kalte Sahne zugeben.',['cream']),machine('Eisbasis verrühren','Basis bei ausgeschalteter Heizung gleichmäßig verrühren; Sahne nicht steif schlagen.',30,0,2),
    prepare('Eisbasis kühlen','Basis in den flachen Behälter umfüllen, abdecken und im Kühlschrank vollständig auf höchstens 5 °C abkühlen lassen. Nach 2 Stunden prüfen und bei Bedarf länger kühlen.'),
    rest('Kühlzeit','Abgedeckte Basis im Kühlschrank 2 Stunden kühlen. Erst kalt in das Gefrierfach stellen.',7200),
    prepare('Gefrieren beginnen','Den vollständig kalten Behälter abgedeckt in das Gefrierfach bei −18 °C stellen.'),
    rest('Erste Gefrierphase','1 Stunde gefrieren lassen.',3600),prepare('Erstes Durchrühren','Mit einer Gabel angefrorene Ränder abkratzen und kräftig in die weichere Mitte einarbeiten. Behälter wieder schließen.'),
    rest('Zweite Gefrierphase','Eine weitere Stunde gefrieren lassen.',3600),prepare('Zweites Durchrühren','Masse erneut von Hand kräftig durchrühren, besonders die gefrorenen Ränder. Wieder abdecken.'),
    rest('Dritte Gefrierphase','Eine weitere Stunde gefrieren lassen.',3600),prepare('Drittes Durchrühren','Noch einmal von Hand durchrühren und den Behälter schließen.'),
    rest('Fest werden lassen','Weitere 2 Stunden gefrieren lassen; bei noch weicher Konsistenz länger warten.',7200),
    finish('Eis portionieren','Vor dem Servieren etwa 10 Minuten im Kühlschrank etwas weicher werden lassen und in 4 Portionen aufteilen. Gefrorene Blöcke nicht im Messer zerkleinern. Ohne Eismaschine wird die Textur fester und kristalliner.')
  ],{prepMinutes:20,cookMinutes:3,restMinutes:430},['Enthält Milch. Matcha enthält Koffein. Aufgetautes Eis nicht erneut einfrieren.']),

  recipe('turmeric-chicken','Kurkuma-Huhn mit Kokossauce',{
    title:'essen & trinken: Geschmortes Kurkuma-Huhn',url:'https://www.essen-und-trinken.de/rezepte/62062-rzpt-geschmortes-kurkuma-huhn',access:'public-recipe',
    adaptation:'Eigenes anderes Gericht mit Hähnchenbrust und Kokossauce. Die Quelle ist eine verwandte Gerichtsidee, kein gefundenes Cookidoo-Original.'
  },[
    ingredient('chicken','Hähnchenbrust',600),ingredient('onion','Zwiebel',120),ingredient('garlic','Knoblauch',8),ingredient('ginger','Ingwer',10),ingredient('coconut','Kokosmilch',400),ingredient('water','Wasser',100),ingredient('oil','Rapsöl',20),ingredient('turmeric','Kurkuma, gemahlen',4),ingredient('salt','Salz',5),ingredient('pepper','Schwarzer Pfeffer, gemahlen',1),ingredient('lemon','Zitronensaft',15)
  ],['mixing-blade','spatula','scale','pan'],[
    prepare('Gemüse vorbereiten','Zwiebel schälen und fein würfeln. Knoblauch und Ingwer schälen und sehr fein hacken.',['onion','garlic','ginger']),
    prepare('Hähnchen vorbereiten','Hähnchenbrust auf einem separaten Brett in etwa 2 cm große Würfel schneiden. Hände, Messer und Brett danach gründlich reinigen.',['chicken']),
    prepare('Gewürze bereitstellen','Kurkuma, Salz und Pfeffer in einer kleinen Schale mischen.',['turmeric','salt','pepper']),
    prepare('Gemüse in der Pfanne anschwitzen','Öl in einer großen Pfanne auf dem Herd bei mittlerer Hitze erwärmen. Vorbereitete Zwiebel 5 Minuten glasig dünsten. Knoblauch, Ingwer und die bereitgestellten Gewürze zugeben und 30 Sekunden mitrühren.',['oil']),
    prepare('Gemüse umfüllen','Angeschwitztes Gemüse samt Gewürzen und Öl vollständig aus der Pfanne in den Maschinentopf geben.'),
    weigh('Sauce ergänzen','Kokosmilch und Wasser in den Maschinentopf einwiegen.',['coconut','water']),machine('Sauce erwärmen','Sauce bei geschlossenem Deckel erhitzen und sanft rühren.',600,95,0.5,'counterclockwise'),
    prepare('Hähnchen in der Pfanne anbraten','Während der Sauce die vorbereiteten Hähnchenwürfel in derselben Pfanne bei mittlerer bis hoher Hitze unter Wenden etwa 4–5 Minuten von allen Seiten anbraten. Die Bräunung allein bestätigt noch keine vollständige Garung.'),
    prepare('Sauce zum Hähnchen geben','Maschinenstillstand abwarten. Heiße Sauce vorsichtig in die Pfanne zum Hähnchen gießen.'),
    finish('Hähnchen fertig garen','In der Pfanne auf dem Herd sanft köcheln lassen, regelmäßig rühren und nach etwa 10–12 Minuten prüfen. In den dicksten Stücken mit einem Küchenthermometer mindestens 70 °C für 2 Minuten sicherstellen; nötigenfalls weitergaren.'),
    finish('Abschmecken und servieren','Zitronensaft unterrühren und in 4 Portionen aufteilen. Vor dem Servieren die vollständige Garung prüfen.',['lemon'])
  ],{prepMinutes:20,cookMinutes:35,restMinutes:0},['Die Pfanne und ein Küchenthermometer werden von Hand bedient. Keine Beilage in den Mengen enthalten.']),

  recipe('xiaomi-mash','Kartoffelbrei mit Käse',{
    title:'Xiaomi-App: Kartoffelbrei, Rezept 1577',url:'https://www.mi.com/global/product/xiaomi-smart-cooking-robot/',access:'local-app-cache',recipeId:'1577',
    adaptation:'Zutaten aus dem vorhandenen Xiaomi-App-Cache. Eigener Ablauf: Kartoffeln auf dem Herd dämpfen und von Hand stampfen. Original verlangt 110 °C und Stufe 5, außerhalb des aktuellen App-Profils.'
  },[
    ingredient('water','Wasser zum Dämpfen',500),ingredient('potatoes','Geschälte Kartoffeln',400),ingredient('salt','Salz',1.5),ingredient('milk','Milch',100),ingredient('cheese','Geriebener Käse',50),ingredient('butter','Butter',15)
  ],['mixing-blade','spatula','scale','pan'],[
    prepare('Kartoffeln vorbereiten','Kartoffeln schälen, die geschälte Menge abwiegen und in etwa 2 cm große Würfel schneiden.',['potatoes']),
    prepare('Dämpftopf vorbereiten','Wasser in einen geeigneten Topf auf dem Herd geben. Einen passenden Dämpfeinsatz einsetzen; die Kartoffeln sollen über dem Wasser liegen. Kartoffeln hineinlegen und Deckel schließen.',['water']),
    finish('Kartoffeln auf dem Herd dämpfen','Wasser auf dem Herd zum Kochen bringen, dann zugedeckt etwa 20–25 Minuten dämpfen. Die Würfel müssen sich mit einer Gabel leicht zerdrücken lassen. Wasserstand beobachten; vor Trockenkochen abbrechen.'),
    weigh('Milch und Butter einwiegen','Milch und Butter in den Maschinentopf einwiegen.',['milk','butter']),machine('Milch erwärmen','Milch und Butter sanft erwärmen.',180,60,1),
    prepare('Kartoffeln stampfen','Weiche Kartoffeln in eine hitzefeste Schüssel geben und von Hand mit einem Kartoffelstampfer zerdrücken. Kochwasser entsorgen; es wird nicht mitgegessen.'),
    prepare('Kartoffelbrei vollenden','Warme Milch-Butter-Mischung, geriebenen Käse und Salz mit einem stabilen Löffel unter die gestampften Kartoffeln rühren. Bei Bedarf weiter stampfen.',['cheese','salt']),
    finish('Servieren','In 4 kleine Beilagenportionen aufteilen und heiß servieren. Kein Hochgeschwindigkeits-Pürieren: Die Konsistenz wird von Hand fertiggestellt.')
  ],{prepMinutes:15,cookMinutes:28,restMinutes:0},['Enthält Milch. Zusätzlich Dämpftopf mit Einsatz und Kartoffelstampfer bereitstellen. Das Dämpfwasser ist Prozesswasser, kein verzehrter Rezeptanteil.'])
];

// A new content identity replaces the previous curated edition for discovery only.
// Existing programs, reviews and in-progress receipts remain immutable.
Object.assign(starterRecipes.find(r=>r.slug==='brownies'),{supersedesRecipeId:'21d9ca2fa1bed9e2f79a51d5f0a2ed9953c9f5451a78b4b03f9f1cd8f136d3e4'});
