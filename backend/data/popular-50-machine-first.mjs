// Editorial revision requested on 2026-10-21: use the appliance's full function
// catalogue. Future function calls are plans, never fabricated motor commands.
const blank={durationSeconds:null,temperatureC:null,speedLevel:null,direction:null};
const action=(kind,title,instruction,uses=[],seconds=null)=>({kind,title,instruction,ingredientUses:uses.map(u=>Array.isArray(u)?{ingredientId:u[0],share:u[1]}:{ingredientId:u,share:1}),settings:{...blank,durationSeconds:seconds}});
const p=(t,s)=>action('prepare',t,s),f=(t,s)=>action('finish',t,s),rest=(t,s,n)=>action('rest',t,s,[],n);
const run=(t,s,n,temp=0,speed=2,reverse=false)=>({...action('machine',t,s),settings:{durationSeconds:n,temperatureC:temp,speedLevel:speed,direction:reverse?'counterclockwise':'clockwise'}});
const future=(id,profile,title,instruction,parameters)=>({...action('function',title,instruction),function:{id,profile,parameters,bindingStatus:'pending',settingsSource:'recipe-specific-binding-required'}});
const meatCheck=p('Garung prüfen','Bei Stillstand mit einem sauberen Küchenthermometer in den größten Fleischstücken mindestens 70 °C für 2 Minuten sicherstellen. Nötigenfalls kontrolliert weitergaren.');
export function applyMachineFirst(recipes){
 for(const r of recipes){
  const byId=new Map(r.ingredients.map(i=>[i.id,i]));
  const add=(ids,dest='in den Mixtopf',title='Zutaten einwiegen',note='')=>{
   const us=ids.map(x=>Array.isArray(x)?x:[x,1]);
   return action('weigh',title,us.map(([id,s])=>{const i=byId.get(id);if(!i)throw Error(r.slug+': '+id);return `${Number((i.amount*s).toFixed(3)).toLocaleString('de-DE')} ${i.unit} ${i.name}`;}).join(', ')+` nacheinander ${dest} geben. ${note}`.trimEnd(),us);
  };
  const replace=(title,...steps)=>{const at=r.steps.findIndex(s=>s.title===title);if(at<0)throw Error(r.slug+': '+title);r.steps.splice(at,1,...steps);};
  const cap=n=>r.capacityAssessment.estimatedPeakMl=n;
  const chopped=(ids,title='Zutaten zerkleinern',sec=5,speed=5)=>[add(ids,'in den kalten Mixtopf'),run(title,'Bei geschlossenem Deckel kurz zerkleinern. Stillstand abwarten und den Rand mit dem Spatel abstreifen; die Stücke sollen erkennbar bleiben.',sec,0,speed)];
  const onion=()=>[p('Zwiebel vorbereiten','Zwiebel schälen; nur große Zwiebeln zum Einfüllen halbieren.'),...chopped(['onion'],'Zwiebel zerkleinern')];
  if(['pizzateig','koernerbrot','naan','zimtschnecken'].includes(r.slug)){
   r.steps[2].instruction=r.steps[2].instruction.replace(/in eine (?:große )?Teigschüssel/,'zum Hefewasser in den Mixtopf');
   if(r.slug==='zimtschnecken')r.steps[2].instruction=r.steps[2].instruction.replace('Hefewasser','Hefemilch');
   r.steps[3]=future('dough',r.slug==='zimtschnecken'?'soft-yeast-dough':'bread-pizza-dough','Teig im Gerät kneten','Deckel schließen und das zum Rezept gehörende Knetprogramm ausführen. Heizung bleibt aus; das Programm übernimmt die wechselnden Messerbewegungen und Pausen. Nach Stillstand prüfen: Der Teig soll zusammenhängend und elastisch sein. Anschließend vollständig in eine Schüssel geben.',{flourGrams:byId.get('flour').amount,recipe:r.slug,target:'zusammenhängender elastischer Hefeteig'});
   cap(1500);r.timing.prepMinutes=Math.max(10,r.timing.prepMinutes-5);
  }
  if(r.slug==='focaccia-popular'){
   r.steps[2]=add(['flour','salt',['oil',0.4]],'zum Hefewasser in den Mixtopf','Mehl ergänzen');
   r.steps.splice(3,6,future('dough','wet-yeast-dough','Focacciateig kneten','Deckel schließen und das rezeptgebundene Knetprogramm für weichen Hefeteig ausführen. Der Teig soll elastisch und klebrig sein. Nach Stillstand vollständig in eine Schüssel umfüllen.',{flourGrams:400,waterGrams:280,recipe:r.slug}),rest('Teig gehen lassen','Schüssel abdecken und 90 Minuten bei Raumtemperatur gehen lassen, bis der Teig sichtbar luftig ist; nötigenfalls länger warten.',5400));cap(1500);
  }
  if(['bananenbrot','muffins','karottenkuchen','schokokuchen'].includes(r.slug)){
   replace('Trockene Zutaten mischen',add(r.steps.find(s=>s.title==='Trockene Zutaten mischen').ingredientUses.map(u=>[u.ingredientId,u.share]),'zur Grundmasse in den Mixtopf','Trockene Zutaten ergänzen','Backpulver gleichmäßig auf das Mehl streuen.'));
   replace('Teig fertigstellen',run('Teig kurz mischen','Nur kurz mischen, bis die trockenen Zutaten verbunden sind. Stillstand abwarten, Mehlreste am Rand mit dem Spatel nach unten schieben; nicht unnötig lange rühren.',20,0,2));
   cap(1800);
   if(r.slug==='karottenkuchen'){
    replace('Möhren und Eier vorbereiten',p('Möhren und Eier vorbereiten','Möhren waschen und schälen. Nur sehr lange Möhren zum Einfüllen teilen. Eier einzeln in einer Schale aufschlagen und Schalenreste entfernen.'),...chopped(['carrot'],'Möhren zerkleinern',8));
    replace('Feuchte Zutaten einwiegen',add(['eggs','oil','sugar'],'zu den Möhren in den Mixtopf','Feuchte Zutaten ergänzen'));
   }
  }
  if(r.slug==='traumstuecke'){
   replace('Mehl abwiegen',add(['flour'],'zur Buttermasse in den Mixtopf','Mehl ergänzen'));
   replace('Mürbeteig formen',future('dough','shortcrust','Mürbeteig im Gerät verbinden','Mit dem kurzen, rezeptgebundenen Mürbeteigprogramm nur so lange verbinden, bis sich zusammenhängende Krümel bilden. Nach Stillstand entnehmen und ohne weiteres Kneten zu einer flachen Platte zusammendrücken.',{flourGrams:250,butterGrams:125,recipe:r.slug}));cap(1000);
  }
  if(r.slug==='cookies'){
   replace('Trockene Zutaten mischen',add(['flour','baking','salt','chocolate'],'zur Buttermasse in den Mixtopf','Trockene Zutaten ergänzen','Backpulver gleichmäßig auf das Mehl streuen.'));
   replace('Teig verbinden',run('Cookie-Teig verbinden','Mit sanftem Linkslauf kurz zu einem weichen Teig verbinden; Schokoladentropfen sollen erhalten bleiben. Stillstand abwarten, Mehlreste vom Rand nach unten schieben und bei Bedarf kurz nachmischen.',20,0,2,true));cap(1100);
  }
  if(r.slug==='scones'){
   replace('Buttermilch abwiegen',add(['buttermilk'],'zu den Mehl-Butter-Krümeln in den Mixtopf','Buttermilch ergänzen'));
   replace('Teig zusammenfügen',run('Sconeteig verbinden','Nur kurz zu groben Teigklumpen verbinden. Stillstand abwarten. Nicht glatt kneten; kleine Butterstücke sollen erhalten bleiben.',10,0,2));cap(1200);
  }
  if(['kuerbissuppe','kartoffelsuppe-wiener','kartoffelgulasch','minestrone'].includes(r.slug)){
   const config={kuerbissuppe:{title:'Kürbis und Kartoffel schneiden',ids:['pumpkin','potato'],rest:['oil','water','salt'],prep:'Kürbis waschen, entkernen und nur so weit teilen, dass er in den Mixtopf passt. Kartoffel waschen und schälen.'},'kartoffelsuppe-wiener':{title:'Gemüse würfeln',ids:['carrot','potato'],rest:['water','salt','oil','marjoram'],prep:'Möhren und Kartoffeln waschen und schälen. Nur sehr große Stücke zum Einfüllen teilen.'},kartoffelgulasch:{title:'Kartoffeln und Paprika würfeln',ids:['potato','pepper'],rest:['tomatopaste','water','salt','paprika'],prep:'Kartoffeln waschen und schälen. Paprika waschen und entkernen; große Stücke zum Einfüllen teilen.'},minestrone:{title:'Gemüse würfeln',ids:['carrot','celery','zucchini'],rest:['tomato','water','oil','salt'],prep:'Gemüse waschen, Möhren schälen und Selleriefäden entfernen. Nur lange Stücke zum Einfüllen teilen. Bohnen abspülen und abtropfen lassen.'}}[r.slug];
   replace(config.title,p('Gemüse vorbereiten',config.prep),add(config.ids,'zur Zwiebel in den Mixtopf','Gemüse einwiegen'),run('Gemüse grob zerkleinern','Kurz zerkleinern. Stillstand abwarten und die größten Stücke kontrollieren. Gleichmäßige grobe Stücke von ungefähr 1–2 cm anstreben; bei Bedarf einen weiteren kurzen Lauf ausführen. Es entstehen unregelmäßige Stücke, keine exakten Würfel.',4,0,4));
   replace(r.slug==='kartoffelgulasch'?'Gulasch ansetzen':'Suppe ansetzen',add(config.rest,'zum Gemüse in den Mixtopf','Garflüssigkeit ergänzen'));
   // For goulash, chop before heating: do not request high motor speed in a hot pot.
   if(r.slug==='kartoffelgulasch'){
    const heat=r.steps.findIndex(s=>s.title==='Zwiebel dünsten');r.steps.splice(heat,1);
    const oil=r.steps.findIndex(s=>s.title==='Öl ergänzen');r.steps.splice(oil,1);
    const after=r.steps.findIndex(s=>s.title==='Gemüse grob zerkleinern')+1;r.steps.splice(after,0,add(['oil'],'zum Gemüse in den Mixtopf','Öl ergänzen'),run('Gemüse dünsten','Gemüse unter sanftem Rühren dünsten.',240,100,1,true));
   }
   if(r.slug==='kartoffelsuppe-wiener')replace('Suppe teilweise stampfen',run('Suppe sämig rühren','Gegarte Kartoffeln kurz bei niedriger Messerstufe verrühren, sodass ein Teil zerfällt und die Suppe bindet. Stillstand abwarten und Konsistenz prüfen. Keine hohe Drehzahl für die heiße Suppe verwenden.',30,0,3));
  }
  if(r.slug==='mandeln'){
   const ids=r.ingredients.map(i=>i.id);
   r.steps=[p('Blech vorbereiten','Ein flaches Backblech mit Backpapier belegen und auf eine hitzefeste Unterlage stellen.'),add(ids,'in den Mixtopf','Mandeln und Zucker einwiegen'),future('caramel','candied-almonds','Mandeln im Gerät karamellisieren','Das rezeptgebundene Programm für gebrannte Mandeln ausführen. Es reduziert die Flüssigkeit, bewegt die Mandeln und lässt die Zuckerschicht kontrolliert anschmelzen. Das Ergebnis soll trocken überzogen und leicht glänzend sein. Nicht durch einen beliebigen Bratlauf ersetzen.',{recipe:r.slug,ingredients:r.ingredients.map(({id,amount,unit})=>({id,amount,unit}))}),f('Mandeln ausbreiten','Nach Programmende und Stillstand sofort vorsichtig auf das vorbereitete Blech geben. Mit zwei Gabeln auseinanderziehen. Heißen Zucker nicht berühren oder probieren.'),rest('Mandeln abkühlen lassen','Mindestens 20 Minuten vollständig abkühlen lassen. Erst feste, abgekühlte Mandeln servieren.',1200)];r.tools=r.tools.filter(t=>t!=='pan');cap(900);
  }
  if(r.slug==='erdbeerkonfituere'){
   replace('Konfitüre aufkochen',future('thicken','jam-boiling-hold','Konfitüre im Gerät kochen','Im Mixtopf mit dem rezeptgebundenen Konfitürenprogramm unter Rühren sprudelnd aufkochen. Die Haltezeit beginnt erst beim tatsächlichen sprudelnden Kochen und entspricht der verwendeten Gelierzuckerpackung. Schaumhöhe beobachten; bei drohendem Überkochen stoppen.',{fruitGrams:500,gellingSugarGrams:250,holdTimeSource:'Gelierzuckerpackung',recipe:r.slug}));r.tools=r.tools.filter(t=>t!=='pan');
  }
  if(r.slug==='beef-tacos'){
   replace('Hackfüllung ansetzen',p('Salsa bereitstellen','Die fertige Salsa in ihrer Servierschale beiseitestellen. Mixtopf mit dem Spatel leeren.'),add(['beef','oil','paprika','cumin',['salt',0.5]],'in den Mixtopf','Hackfüllung einwiegen'));
   replace('Füllung braten',run('Hackfüllung im Gerät braten','Mit der Bratfunktion bei sanftem Linkslauf anbraten. Nach Stillstand grobe Klumpen mit dem Spatel lösen.',600,140,1,true),meatCheck);
  }
  if(r.slug==='teriyaki-haehnchen'){
   r.steps=[...chopped(['ginger','garlic'],'Aromaten zerkleinern',6,6),add(['soy','water','honey','vinegar','starch'],'zu den Aromaten in den Mixtopf','Sauce ansetzen'),run('Sauce mischen','Stärke in der kalten Sauce gleichmäßig verteilen.',25,0,3),p('Sauce bereitstellen','Die kalte Sauce vollständig in eine Schüssel umfüllen. Mixtopf ausspülen und außen trocknen.'),p('Hähnchen vorbereiten','Hähnchenbrust in etwa 2 cm große Stücke schneiden; der Messerlauf würde statt gleichmäßiger Fleischstücke Hack erzeugen. Brett, Messer und Hände danach reinigen.'),add(['oil','chicken'],'in den Mixtopf','Hähnchen einwiegen'),run('Hähnchen anbraten','Mit der Bratfunktion unter sanftem Linkslauf anbraten; Fleischstücke sollen erhalten bleiben.',480,140,1,true),p('Sauce zurückgeben','Vorbereitete Sauce kurz durchrühren und vollständig zum Hähnchen in den Mixtopf gießen.'),run('Hähnchen in Sauce garen','Unter sanftem Rühren garen, bis die Sauce bindet und das Hähnchen durchgegart ist.',600,100,0.5,true),meatCheck,f('Teriyaki-Hähnchen servieren','Mit der gesamten Sauce auf vier Portionen verteilen; keine Reisbeilage enthalten.')];r.tools=r.tools.filter(t=>t!=='pan');cap(1600);
  }
  if(r.slug==='butter-chicken'){
   const prior=r.steps;
   r.steps=[...prior.slice(0,2),p('Aromaten vorbereiten','Zwiebel schälen. Ingwer und Knoblauch schälen; nur große Stücke zum Einfüllen teilen.'),...chopped(['onion','garlic','ginger'],'Aromaten zerkleinern',6,6),add(['butter','oil'],'zu den Aromaten in den Mixtopf','Fett ergänzen'),run('Aromaten dünsten','Aromaten unter Rühren in Butter und Öl dünsten.',240,100,1,true),p('Mariniertes Hähnchen zugeben','Die gesamte Hähnchen-Joghurt-Marinade aus der Schüssel in den Mixtopf geben. Schüssel und verwendete Werkzeuge anschließend reinigen.'),run('Hähnchen anschwitzen','Hähnchen mit den Aromaten unter sanftem Linkslauf erhitzen.',300,120,1,true),add(['tomato',['garam',0.5],['salt',0.5]],'zum Hähnchen in den Mixtopf','Sauce ergänzen'),run('Butter Chicken garen','Bei geschlossenem Deckel unter sanftem Rühren durchgaren.',900,100,0.5,true),add(['cream'],'in den Mixtopf','Sahne ergänzen'),run('Sauce abrunden','Sahne einrühren und das Gericht gleichmäßig erwärmen.',180,90,0.5,true),meatCheck,f('Butter Chicken servieren','In vier Portionen mit Sauce servieren; Reis oder Brot sind nicht enthalten.')];r.tools=r.tools.filter(t=>t!=='pan');cap(2000);
  }
  if(r.slug==='rind-stroganoff'){
   r.steps=[p('Gemüse vorbereiten','Zwiebel schälen. Champignons putzen. Gewürzgurken abtropfen lassen.'),...chopped(['pickles'],'Gurken grob zerkleinern',3,4),p('Gurken umfüllen','Gurken vollständig in eine kleine Schale geben.'),...chopped(['mushroom'],'Pilze grob zerkleinern',3,4),p('Pilze umfüllen','Pilze vollständig in eine Schüssel geben.'),...onion(),add(['oil','beef'],'zur Zwiebel in den Mixtopf','Fleisch einwiegen'),run('Fleisch im Gerät anbraten','Rindergeschnetzeltes unter sanftem Linkslauf anbraten.',480,140,1,true),p('Pilze zurückgeben','Die vorbereiteten Pilze vollständig zum Fleisch geben.'),run('Pilze mitbraten','Pilze unter Rühren mitbraten.',300,140,1,true),add(['cream','water','mustard','salt','starch'],'in eine Schüssel','Sauce vorbereiten','Stärke mit einem kleinen Schneebesen vollständig in der kalten Flüssigkeit verteilen.'),p('Sauce und Gurken zugeben','Die gesamte angerührte Sauce und die vorbereiteten Gurken in den Mixtopf geben.'),run('Stroganoff fertig garen','Bei geschlossenem Deckel schonend garen, bis die Sauce bindet.',420,100,0.5,true),meatCheck,f('Stroganoff servieren','Geschnetzeltes und Sauce auf vier Portionen verteilen; keine Beilage enthalten.')];r.tools=r.tools.filter(t=>t!=='pan');cap(2000);
  }
  if(r.slug==='lasagne'){
   const tail=r.steps.slice(9);
   r.steps=[p('Gemüse vorbereiten','Zwiebel schälen. Möhre waschen und schälen; zum Einfüllen gegebenenfalls teilen.'),...chopped(['onion','carrot'],'Gemüse zerkleinern'),add(['beef','oil'],'zum Gemüse in den Mixtopf','Hackfleisch ergänzen'),run('Bolognese anbraten','Mit der Bratfunktion unter Linkslauf anbraten. Nach Stillstand große Hackfleischklumpen mit dem Spatel lösen.',600,140,1,true),add(['tomato','oregano',['salt',5/9]],'zum Hackfleisch in den Mixtopf','Tomaten ergänzen'),run('Bolognese garen','Bolognese bei geschlossenem Deckel sanft garen.',900,100,0.5,true),meatCheck,p('Bolognese umfüllen','Die Bolognese vollständig in eine hitzefeste Schüssel geben und abdecken. Mixtopf ausspülen, Speisereste entfernen und außen trocknen.'),...tail];r.tools=r.tools.filter(t=>t!=='pan');cap(1800);
  }
  if(r.slug==='pizzasuppe'){
   r.steps=[p('Gemüse vorbereiten','Zwiebel schälen. Paprika waschen und entkernen; zum Einfüllen vierteln.'),...chopped(['onion','pepper'],'Gemüse grob zerkleinern',4,4),add(['oil','beef'],'zum Gemüse in den Mixtopf','Hackfleisch ergänzen'),run('Hackfleisch im Gerät anbraten','Mit der Bratfunktion unter Linkslauf anbraten. Nach Stillstand grobe Hackfleischklumpen mit dem Spatel lösen.',540,140,1,true),add(['tomato','water','corn','salt','oregano'],'in den Mixtopf','Suppe ansetzen'),run('Pizzasuppe garen','Bei geschlossenem Deckel sanft garen.',1200,100,0.5,true),add(['creamcheese'],'zur Suppe in den Mixtopf','Frischkäse ergänzen'),run('Frischkäse einrühren','Frischkäse unter Rühren in der heißen Suppe auflösen.',120,90,1,true),meatCheck,f('Pizzasuppe servieren','Heiße Suppe mit Hackfleisch gleichmäßig auf vier Schalen verteilen.')];r.tools=r.tools.filter(t=>t!=='pan');cap(2000);
  }
  if(r.slug==='smashed-potatoes'){
   byId.get('water').amount=1000;byId.get('water').name='Wasser zum Dampfgaren';
   const end=r.steps.slice(5);
   r.steps=[p('Kartoffeln waschen','Kleine Kartoffeln mit Schale gründlich waschen und schadhafte Stellen entfernen.'),add(['garlic','oil','salt'],'in den kalten Mixtopf','Knoblauchöl ansetzen'),run('Knoblauchöl mixen','Knoblauch im Öl kurz zerkleinern. Bei Stillstand vom Rand abstreifen.',10,0,5),p('Knoblauchöl umfüllen','Knoblauchöl vollständig in eine Schale geben. Mixtopf ausspülen und außen trocknen.'),add(['water'],'in den Mixtopf','Dampfwasser einwiegen'),add(['potatoes'],'in den Dampfgaraufsatz','Kartoffeln einlegen','Dampföffnungen teilweise frei lassen und Aufsatz vollständig aufsetzen.'),future('steam','whole-potatoes','Kartoffeln im Gerät dämpfen','Das rezeptgebundene Dampfgarprogramm für kleine ganze Kartoffeln ausführen. Nach Ende vorsichtig vom Körper weg öffnen und die größte Kartoffel mit einer Gabel prüfen: Sie muss bis zur Mitte weich sein. Bei Bedarf mit ausreichendem Restwasser weiterdämpfen. Kartoffeln kurz ausdampfen lassen; Restwasser später verwerfen.',{waterGrams:1000,potatoGrams:700,target:'bis zur Mitte weich',recipe:r.slug}),...end];r.tools=[...r.tools.filter(t=>t!=='pan'),'steamer'];cap(1200);
  }
  if(r.slug==='coleslaw'){
   const end=r.steps.slice(5);
   r.steps=[p('Gemüse vorbereiten','Kohl waschen und den Strunk entfernen. Möhren waschen und schälen. Kohl nur zum Einfüllen in grobe Stücke teilen.'),...chopped(['cabbage','carrot'],'Kohl und Möhren zerkleinern',5,4),add(['yogurt','mayo','vinegar','sugar','salt'],'zum Gemüse in den Mixtopf','Dressingzutaten ergänzen'),run('Coleslaw mischen','Mit sanftem Linkslauf vermengen. Stillstand abwarten und bei Bedarf vom Rand mit dem Spatel einarbeiten.',40,0,1,true),p('Salat umfüllen','Salat vollständig in eine Schüssel geben.'),...end];cap(1800);
  }
  if(r.slug==='zaziki'){
   const tail=r.steps.slice(3,7);
   r.steps=[p('Gurke vorbereiten','Gurke waschen, Enden entfernen und nur zum Einfüllen in grobe Stücke teilen.'),...chopped(['cucumber'],'Gurke zerkleinern',4,4),p('Gurke umfüllen','Gurke vollständig in ein Sieb über einer Schüssel geben.'),add([['salt',0.5]],'zur Gurke im Sieb','Gurke salzen','Mit einem Löffel verteilen.'),rest('Gurke abtropfen lassen','10 Minuten ziehen lassen, dann mit einem Löffel im Sieb gut ausdrücken. Ausgetretenes Wasser verwerfen.',600),...tail,p('Gurke zurückgeben','Ausgedrückte Gurke vollständig zur Joghurtmasse in den Mixtopf geben.'),run('Zaziki mischen','Gurkenstücke mit sanftem Linkslauf in der Joghurtmasse verteilen.',20,0,1,true),f('Zaziki servieren','In eine Schale umfüllen und bis zum Servieren abgedeckt kalt stellen.')];cap(1000);
  }
  if(r.slug==='tomatensauce'){
   replace('Zwiebel einwiegen',add(['onion','garlic'],'in den kalten Mixtopf','Zwiebel und Knoblauch einwiegen'));
   r.steps=r.steps.filter(s=>s.title!=='Knoblauch schneiden');
  }
  if(r.slug==='zucchinischnitte'){
   const tail=r.steps.slice(5);
   r.steps=[p('Zucchini vorbereiten','Zucchini waschen und die Enden entfernen. Nur zum Einfüllen in grobe Stücke teilen.'),...chopped(['zucchini'],'Zucchini zerkleinern',4,4),p('Zucchini abtropfen lassen','Zucchini vollständig in ein Sieb geben, mit einem Löffel leicht ausdrücken und beiseitestellen.'),add(['eggs','oil'],'in den Mixtopf','Eiermasse ansetzen','Eier zuvor einzeln aufschlagen und Schalenreste entfernen.'),run('Eier mischen','Eier und Öl gleichmäßig verrühren.',30,0,3),add(['cheese','flour','salt','baking'],'zur Eiermasse in den Mixtopf','Trockene Zutaten ergänzen'),p('Zucchini zurückgeben','Abgetropfte Zucchini vollständig in den Mixtopf geben.'),run('Masse verbinden','Alle Zutaten kurz mit sanftem Linkslauf verbinden.',30,0,2,true),...tail];cap(1600);
  }
  if(r.slug==='hackbaellchen-toskana'){
   replace('Hackmasse vorbereiten',p('Sauce umfüllen','Die fertige Sauce vollständig in eine hitzefeste Schüssel geben. Mixtopf kurz ausspülen und außen trocknen.'),add(['beef','breadcrumbs',['salt',4/7]],'in den Mixtopf','Hackmasse einwiegen'));
   replace('Ei und Hackfleisch mischen',action('prepare','Ei zugeben','Ei einzeln in einer Schale aufschlagen und ohne Schalenreste zur Hackmasse in den Mixtopf geben.',['egg']),run('Hackmasse mischen','Kurz mit Linkslauf gleichmäßig vermischen. Stillstand abwarten, bevor die Masse entnommen wird.',40,0,2,true));
   replace('Sauce angießen',p('Sauce angießen','Die beiseitegestellte Tomatensauce vollständig über die geformten Bällchen gießen.'));
  }
  if(r.slug==='bananenbrot')replace('Bananen und Eier vorbereiten',p('Bananen und Eier vorbereiten','Bananen schälen und zum Einfüllen grob brechen; das Gerät übernimmt das Zerdrücken beim Mischen. Eier einzeln in einer Schale aufschlagen und Schalenreste entfernen.'));
  if(r.slug==='ingwershot')replace('Ingwer schneiden',p('Ingwer vorbereiten','Ingwer waschen und schälen. Nur sehr große Knollen zum Einfüllen grob teilen; das feine Zerkleinern übernimmt das Gerät.'));
  if(r.slug==='brokkolisalat')replace('Gemüse und Apfel vorbereiten',p('Gemüse und Apfel vorbereiten','Brokkoli, Paprika und Apfel waschen. Paprika und Apfel entkernen. Brokkoli in Röschen teilen; Apfel und Paprika nur zum Einfüllen grob teilen. Das Gerät übernimmt das feine Zerkleinern.'));
  if(r.slug==='brokkolisalat')r.steps.find(s=>s.title==='Rohkost zerkleinern').instruction='Sehr kurz zerkleinern. Stillstand abwarten: Die Stücke sollen körnig bleiben und kein Mus werden. Große Stücke mit dem Spatel nach unten schieben und bei Bedarf einen weiteren kurzen Messerlauf ausführen.';
  if(r.slug==='kichererbsensalat'){
   r.steps=[p('Gemüse vorbereiten','Gurke, Tomaten und Petersilie waschen. Zwiebel schälen. Gurke und Tomaten nur zum Einfüllen grob teilen. Kichererbsen abspülen und abtropfen lassen.'),...chopped(['onion','parsley'],'Zwiebel und Petersilie zerkleinern',5,5),add(['lemon','oil','salt'],'in den Mixtopf','Dressing ergänzen'),run('Dressing verrühren','Dressing gleichmäßig mischen.',20,0,3),add(['cucumber','tomato'],'in den Mixtopf','Gemüse ergänzen'),run('Salatgemüse grob zerkleinern','Sehr kurz zerkleinern; nach Stillstand prüfen. Gurke und Tomate sollen grobstückig bleiben und nicht püriert werden.',3,0,4),add(['chickpeas'],'in den Mixtopf','Kichererbsen ergänzen'),run('Salat vermengen','Mit sanftem Linkslauf vermengen, Kichererbsen ganz lassen.',20,0,0.5,true),f('Salat servieren','Salat gleichmäßig auf vier Portionen verteilen.')];cap(1800);
  }
  if(r.slug==='guacamole'){
   const original=r.steps;
   const avocadoAdd=original.find(s=>s.kind==='weigh'&&s.ingredientUses.some(u=>u.ingredientId==='avocado'));
   const otherIds=original.flatMap(s=>s.ingredientUses).filter(u=>!['onion','tomato'].includes(u.ingredientId)).map(u=>[u.ingredientId,u.share]);
   r.steps=[p('Gemüse vorbereiten','Zwiebel schälen. Tomate waschen, Strunk entfernen und zum Einfüllen halbieren. Avocado halbieren, Kern entfernen und Fruchtfleisch aus der Schale lösen.'),...chopped(['onion'],'Zwiebel zerkleinern',5,5),add(['tomato'],'in den Mixtopf','Tomate ergänzen'),run('Tomate grob hacken','Tomate kurz grob zerkleinern, nicht pürieren.',2,0,4),p('Tomatenmischung umfüllen','Zwiebel-Tomaten-Mischung vollständig in eine Schale geben.'),add(otherIds,'in den Mixtopf','Avocadobasis ansetzen'),run('Avocado cremig rühren','Avocado mit den Gewürzen kurz cremig verrühren; kleine Stückchen dürfen bleiben.',20,0,3),p('Tomatenmischung zurückgeben','Zwiebel-Tomaten-Mischung vollständig zurück in den Mixtopf geben.'),run('Guacamole fertig mischen','Mit sanftem Linkslauf gleichmäßig vermengen.',15,0,1,true),f('Guacamole servieren','Sofort in einer Schale servieren oder dicht abgedeckt kalt stellen.')];cap(1400);
  }
  if(['spaghetti-sahne','spaghetti-tomate-mozzarella'].includes(r.slug)){
   byId.get('water').amount=1500;
   const original=r.steps;
   const sauce=r.slug==='spaghetti-sahne'?[...original.slice(0,8),...original.slice(10,12)]:[...original.slice(0,5),...original.slice(7,9)];
   const end=r.slug==='spaghetti-sahne'?[]:[original.find(s=>s.title==='Käse und Basilikum vorbereiten')];
   r.steps=[...sauce,p('Sauce warm halten','Die fertige Sauce vollständig in eine große hitzefeste Servierschüssel geben und abdecken. Mixtopf ausspülen und außen trocknen.'),add(['water',['salt',5/6]],'in den Mixtopf','Nudelwasser einwiegen'),run('Nudelwasser aufkochen','Wasser bei geschlossenem Deckel erhitzen. Nach Stillstand prüfen, ob es tatsächlich kocht; falls nötig kontrolliert weiter erhitzen.',720,100,0.5,true),add(['pasta'],'in das heiße Wasser im Mixtopf','Spaghetti zugeben','Spaghetti zum vollständigen Einlegen halbieren. Deckel schließen.'),run('Spaghetti im Gerät garen','Mit sanftem Linkslauf garen. Voreingestellte 10 Minuten vor dem Start mit der Packungszeit abgleichen. Nach Stillstand eine Nudel auf Biss prüfen; nötigenfalls kontrolliert weitergaren.',600,100,0.5,true),p('Spaghetti abgießen','Maschinenstillstand abwarten. Spaghetti vorsichtig durch ein hitzefestes Sieb abgießen; Kochwasser verwerfen.'),...end,f('Pasta vermengen','Heiße Spaghetti zur beiseitegestellten Sauce geben, alles vorsichtig vermengen und sofort in vier Portionen servieren.')];r.tools=r.tools.filter(t=>t!=='pan');cap(2100);
  }
  // Final operation audit: washing/peeling is preparation. Small-cut work belongs
  // to a machine step, not hidden in preparation or weighing instructions.
  for(const s of r.steps){
   if(s.title==='Zwiebel vorbereiten')s.instruction='Zwiebel schälen. Das Zerkleinern übernimmt anschließend das Gerät.';
   s.instruction=s.instruction.replace(/Butter (?:vorher )?in (?:kleine|1 cm große) Würfel schneiden[.;]?\s*/g,'').replace(/Butter klein würfeln[.;]?\s*/g,'');
  }
  if(r.slug==='erdbeerkonfituere')replace('Erdbeeren vorbereiten',p('Erdbeeren vorbereiten','Erdbeeren waschen und die Kelchblätter entfernen. Die ganzen geputzten Früchte werden anschließend im Gerät zerkleinert.'));
  if(r.slug==='pilzrisotto'){
   replace('Pilze schneiden',p('Pilze vorbereiten','Champignons putzen und schadhafte Stellen entfernen. Ganze Champignons bereitstellen.'));
   replace('Pilze zugeben',add(['mushroom'],'zur Zwiebel in den kalten Mixtopf','Champignons einwiegen'),run('Champignons zerkleinern','Ganze Champignons kurz grob zerkleinern. Stillstand abwarten und prüfen: Es sollen erkennbare Pilzstücke bleiben, kein Pilzmus.',3,0,4),add(['oil'],'in den Mixtopf','Öl ergänzen'));
   replace('Risotto cremig rühren',run('Risotto cremig rühren','Butter und Parmesan mit sanftem Linkslauf in das Risotto einarbeiten.',25,0,0.5,true));
  }
  if(r.slug==='gemuesewuerzpaste')replace('Gemüse vorbereiten',p('Gemüse vorbereiten','Lauch und Petersilie gründlich waschen. Möhren und Sellerie schälen, Zwiebel abziehen. Geputztes Gemüse und getrocknete Tomaten bereitstellen; das Gerät übernimmt das Zerkleinern. Große Selleriestücke müssen beim Einfüllen unter die MAX-Markierung passen.'));
  if(r.slug==='beef-tacos'){
   replace('Tomaten vorbereiten',p('Tomaten vorbereiten','Tomaten waschen und die Stielansätze entfernen. Das Gerät übernimmt das Zerkleinern.'));
   replace('Toppings bereitstellen',add(['cheese','taco'],'in getrennte Servierschalen','Toppings bereitstellen'));
   r.steps.unshift(p('Salat vorbereiten','Salat waschen, abtropfen lassen und die Blätter vom Strunk lösen.'),...chopped(['lettuce'],'Salat grob zerkleinern',2,4),p('Salat umfüllen','Grob zerkleinerten Salat vollständig in eine Servierschale umfüllen und kalt stellen.'));
   r.steps=r.steps.filter(s=>s.title!=='Salsa bereitstellen');
  }
  if(r.slug==='minestrone')replace('Basilikum vorbereiten',action('prepare','Basilikum ergänzen','Basilikum waschen, trocken schütteln und die ganzen Blätter bei Stillstand in die fertige Suppe geben.',['basil']));
  if(r.slug==='kartoffelgratin'){
   r.steps=[p('Kartoffeln vorbereiten','Kartoffeln waschen und schälen. Backofen auf 180 °C Ober-/Unterhitze vorheizen.'),add([['potato',0.5]],'in den kalten Mixtopf','Erste Kartoffelportion einwiegen'),run('Kartoffeln grob zerkleinern','400 g Kartoffeln kurz grob zerkleinern. Stillstand abwarten: Es sollen ungefähr 1–2 cm große unregelmäßige Stücke entstehen, kein Püree.',4,0,4),p('Kartoffeln umfüllen','Erste Portion vollständig in eine Schüssel geben.'),add([['potato',0.5]],'in den kalten Mixtopf','Zweite Kartoffelportion einwiegen'),run('Weitere Kartoffeln zerkleinern','Die zweite Portion ebenfalls kurz grob zerkleinern und nach Stillstand die Größe prüfen.',4,0,4),p('Kartoffeln bereitstellen','Die zweite Portion zur ersten in die Schüssel geben.'),add(['garlic','milk','cream','salt','nutmeg'],'in den kalten Mixtopf','Guss ansetzen'),run('Guss verrühren','Knoblauch im Guss fein zerkleinern.',15,0,5),run('Guss erwärmen','Milch-Sahne-Guss erwärmen.',300,80,1),add(['butter'],'in eine etwa 20 × 30 cm große Auflaufform mit Rand','Form buttern','Butter über Boden und Ränder verstreichen.'),p('Gratin zusammensetzen','Alle Kartoffelstücke gleichmäßig in der Form verteilen. Den gesamten heißen Guss vorsichtig darübergeben. Diese Gerätefassung verwendet grobe Kartoffelstücke.'),f('Gratin backen','Bei 180 °C Ober-/Unterhitze 45–60 Minuten backen. Eine Gabel muss ohne Widerstand durch die größten Kartoffelstücke gleiten. Bei dunkler Oberfläche locker abdecken und weiterbacken.'),rest('Gratin setzen lassen','10 Minuten außerhalb des Ofens ruhen lassen, dann in vier Portionen teilen.',600)];cap(1100);
  }
  if(r.slug==='spaghetti-sahne'){
   r.steps=r.steps.filter(s=>s.title!=='Schinken schneiden');
   replace('Sauce ansetzen',add(['cream','pepper',['salt',1/6]],'zur Zwiebel in den Mixtopf','Sauce ansetzen'),p('Schinken zurückgeben','Den zuvor zerkleinerten Schinken vollständig zur Sauce in den Mixtopf geben.'));
   r.steps.unshift(...chopped(['ham'],'Schinken grob zerkleinern',3,4),p('Schinken bereitstellen','Schinken vollständig in eine Schale umfüllen.'));
  }
  if(r.slug==='kartoffelsuppe-wiener'){
   r.steps=r.steps.filter(s=>s.title!=='Würstchen schneiden');
   replace('Würstchen zugeben',p('Würstchen zurückgeben','Die zuvor grob zerkleinerten Wiener vollständig in die Suppe geben.'));
   r.steps.unshift(...chopped(['sausage'],'Wiener grob zerkleinern',2,3),p('Wiener bereitstellen','Würstchenstücke vollständig in eine Schale geben und bis zur Zugabe kalt stellen. Sie sind unregelmäßig und keine geschnittenen Scheiben.'));
  }
  if(['pizzateig','spaghetti-tomate-mozzarella','hackbaellchen-toskana'].includes(r.slug)){
   if(r.slug==='pizzateig')replace('Belag bereitstellen',add(['tomato','oregano'],'in getrennte Schalen','Belag bereitstellen'));
   if(r.slug==='hackbaellchen-toskana')replace('Mozzarella abwiegen',p('Mozzarella verteilen','Den vorbereiteten Mozzarella aus der Schale gleichmäßig auf den Bällchen verteilen.'));
   if(r.slug==='spaghetti-tomate-mozzarella')replace('Käse und Basilikum vorbereiten',action('prepare','Käse und Basilikum ergänzen','Basilikum waschen und trocken schütteln. Die ganzen Blätter und den vorbereiteten Mozzarella zur Sauce in der Servierschüssel geben.',['basil']));
   r.steps.unshift(add(['mozzarella'],'in den kalten Mixtopf','Mozzarella einwiegen'),run('Mozzarella zerkleinern','Abgetropften Mozzarella kurz grob zerkleinern. Nach Stillstand prüfen; kleine unregelmäßige Stücke sollen erhalten bleiben.',3,0,4),p('Mozzarella bereitstellen','Mozzarella vollständig in eine Schale umfüllen und bis zur Verwendung kalt stellen.'));
  }
  if(r.slug==='datteldip')replace('Konsistenz prüfen',p('Konsistenz prüfen','Stillstand abwarten. Topfwand mit dem Spatel abstreifen und die Konsistenz prüfen; der Dip soll cremig sein.'));
  if(r.slug==='pfannkuchen')replace('Teig ruhen lassen',rest('Teig ruhen lassen','Teig 15 Minuten im Mixtopf ruhen lassen.',900),run('Teig glatt rühren','Nach der Ruhezeit kurz glatt rühren. Nach Stillstand in eine Schüssel umfüllen.',15,0,3));
  if(r.slug==='porridge')replace('Porridge servieren',run('Honig einrühren','Honig mit sanftem Linkslauf gleichmäßig verteilen.',15,0,0.5,true),f('Porridge servieren','Porridge in vier Schalen füllen und etwas abkühlen lassen.'));
  const preparation={
   ingwershot:['Ingwer vorbereiten','Ingwer waschen und schälen. Das feine Zerkleinern übernimmt das Gerät.'],
   brokkolisalat:['Gemüse und Apfel vorbereiten','Brokkoli, Paprika und Apfel waschen. Paprika und Apfel entkernen. Brokkoliröschen bereitstellen. Das Gerät übernimmt das Zerkleinern.'],
   kichererbsensalat:['Gemüse vorbereiten','Gurke, Tomaten und Petersilie waschen. Zwiebel schälen und Stielansätze entfernen. Kichererbsen abspülen und abtropfen lassen.'],
   zucchinischnitte:['Zucchini vorbereiten','Zucchini waschen und die Enden entfernen. Das Gerät übernimmt das Zerkleinern.'],
   guacamole:['Gemüse vorbereiten','Zwiebel schälen. Tomate waschen und den Strunk entfernen. Avocado öffnen, Kern entfernen und Fruchtfleisch aus der Schale lösen.'],
   coleslaw:['Gemüse vorbereiten','Kohl waschen, die benötigten Blätter vom Strunk lösen. Möhren waschen und schälen. Das Zerkleinern übernimmt das Gerät.'],
   zaziki:['Gurke vorbereiten','Gurke waschen und die Enden entfernen. Das Gerät übernimmt das Zerkleinern.'],
   pizzasuppe:['Gemüse vorbereiten','Zwiebel schälen. Paprika waschen und entkernen. Das Gerät übernimmt das Zerkleinern.'],
   minestrone:['Gemüse vorbereiten','Gemüse waschen, Möhren schälen und Selleriefäden entfernen. Bohnen abspülen und abtropfen lassen.'],
   kuerbissuppe:['Gemüse vorbereiten','Kürbis waschen und entkernen. Kartoffel waschen und schälen. Die angegebene Gemüsemenge wird anschließend im Gerät zerkleinert.'],
   kartoffelgulasch:['Gemüse vorbereiten','Kartoffeln waschen und schälen. Paprika waschen und entkernen. Das Gerät übernimmt das Zerkleinern.'],
   'kartoffelsuppe-wiener':['Gemüse vorbereiten','Möhren und Kartoffeln waschen und schälen. Das Gerät übernimmt das Zerkleinern.']
  }[r.slug];
  if(preparation)replace(preparation[0],p(...preparation));
  // Dough shaping and the dimensions of whole meat pieces remain intentional
  // manual tasks. They are not a substitute for vegetable chopping or kneading.
  r.requiredFunctions=[...new Set(r.steps.flatMap(s=>s.function?[s.function.id]:s.kind==='machine'?[s.settings.temperatureC>100?'fry':s.settings.temperatureC>0?'warm':s.settings.speedLevel>=4?'chop':'mix']:[]))];
  r.pendingBindings=r.steps.filter(s=>s.function).map(s=>({...s.function,stepIndex:r.steps.indexOf(s)}));
  r.editorial={...r.editorial,revision:'machine-first-2026-10-21',originalQuantities:false,images:'pending-rebuild',cookingTested:false};
 }
}
