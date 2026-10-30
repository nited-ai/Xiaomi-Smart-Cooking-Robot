// Reference: https://kalomatik.pro/#kochschritte, observed 2026-10-19.
// These are design-example settings, NOT an approved Xiaomi controller profile.
const source='https://kalomatik.pro/#kochschritte';
const image=(name,alt)=>({url:`https://kalomatik.pro/assets/${name}-v19.webp`,alt,kind:'illustration',source});
const ingredient=(name,amount,unit)=>({name,amount,unit});
const manual={durationSeconds:null,temperatureC:null,speedLevel:null,direction:null};
const steps=[
  {id:'prepare',kind:'weigh',title:'Zutaten bereitstellen',instruction:'Die Zutaten für vier Portionen abwiegen. Tomaten grob schneiden und das Messer einsetzen.',
    ingredients:[ingredient('Risottoreis',320,'g'),ingredient('Tomaten',600,'g'),ingredient('Zwiebel',120,'g'),ingredient('Gemüsebrühe',800,'ml'),ingredient('Olivenöl',40,'g'),ingredient('Parmesan',60,'g'),ingredient('Basilikum',8,'g'),ingredient('Salz',4,'g'),ingredient('Pfeffer',1,'g')],image:image('risotto-prep','Bereitgestellte Zutaten für das Tomatenrisotto'),settings:{...manual}},
  {id:'onion',kind:'machine',title:'Zwiebel zerkleinern',instruction:'Zwiebel schälen, vierteln und in den Topf geben. Deckel schließen. Anschließend mit dem Spatel nach unten schieben.',
    ingredients:[ingredient('Zwiebel',120,'g')],image:image('risotto-onion','Zerkleinerte Zwiebel im Topf'),settings:{durationSeconds:6,temperatureC:0,speedLevel:5,direction:'clockwise'}},
  {id:'roast',kind:'machine',title:'Zwiebel und Reis anrösten',instruction:'Olivenöl und Risottoreis zugeben. Fünf Minuten unter sanftem Rühren anrösten.',
    ingredients:[ingredient('Olivenöl',40,'g'),ingredient('Risottoreis',320,'g')],image:image('risotto-roast','Reis und Zwiebel beim Anrösten'),settings:{durationSeconds:300,temperatureC:180,speedLevel:3,direction:'counterclockwise'}},
  {id:'simmer',kind:'machine',title:'Tomaten und Brühe zugeben',instruction:'Tomaten und Gemüsebrühe zugeben. Deckel schließen und unter sanftem Rühren garen. Gegen Ende die Konsistenz prüfen.',
    ingredients:[ingredient('Tomaten',600,'g'),ingredient('Gemüsebrühe',800,'ml')],image:image('risotto-simmer','Tomatenrisotto beim Garen'),settings:{durationSeconds:1080,temperatureC:100,speedLevel:1,direction:'counterclockwise'}},
  {id:'finish',kind:'finish',title:'Abschmecken. Servieren.',instruction:'Parmesan und Basilikum unterheben. Mit Salz und Pfeffer abschmecken. Zwei Minuten ruhen lassen und auf vier Teller verteilen.',
    ingredients:[ingredient('Parmesan',60,'g'),ingredient('Basilikum',8,'g'),ingredient('Salz',4,'g'),ingredient('Pfeffer',1,'g')],image:image('risotto-finish','Fertiges Tomatenrisotto im Topf'),settings:{...manual,durationSeconds:120}}
];
// The site states time/temperature/level. Direction is an explicit, unreviewed
// design proposal here; it is not proof of a calibrated C3 speed mapping.
const sourceSnapshot=JSON.stringify({title:'Tomatenrisotto für vier',steps});
const revision=[...new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(sourceSnapshot)))].map(b=>b.toString(16).padStart(2,'0')).join('');
export const proExample={version:1,id:'kalomatik-pro-risotto-study',title:'Tomatenrisotto',source:{url:source,recipeId:'pro-risotto-example',revision},deviceModel:'xiaomi-c3',servings:4,tools:['mixing-blade'],cover:{url:'https://kalomatik.pro/assets/recipe-risotto-v5.webp',alt:'Tomatenrisotto',kind:'illustration',source},steps};
