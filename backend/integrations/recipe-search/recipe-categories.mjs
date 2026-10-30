/** Public taxonomy projection. Membership is bound to the reviewed ingredient/title identity. */
export function publicCategories(catalog,rows){
 const valid=new Map(rows.filter(r=>r.guideStatus==='ready').map(r=>[r.id,r]));
 const definitions=Array.isArray(catalog?.categories)?catalog.categories.slice(0,100):[];
 const groups=new Map();
 for(const c of definitions){if(!c||!/^[a-z0-9-]{1,64}$/.test(c.id)||typeof c.title!=='string'||!c.title.trim()||c.title.length>80||/[\x00-\x1f@]|https?:|www\./i.test(c.title)||groups.has(c.id))continue;groups.set(c.id,{id:c.id,title:c.title,default:c.default===true,recipeIds:[],foodIdentities:[]});}
 for(const a of Array.isArray(catalog?.assignments)?catalog.assignments.slice(0,5000):[]){const row=valid.get(a?.recipeId);if(!row||row.foodIdentity!==a.foodIdentity||!Array.isArray(a.categoryIds))continue;for(const id of new Set(a.categoryIds)){const c=groups.get(id);if(c&&!c.recipeIds.includes(row.id)){c.recipeIds.push(row.id);if(!c.foodIdentities.includes(row.foodIdentity))c.foodIdentities.push(row.foodIdentity);}}}
 return {schemaVersion:1,revision:typeof catalog?.revision==='string'?catalog.revision.slice(0,80):'',categories:[...groups.values()].map(c=>({...c,count:c.foodIdentities.length}))};
}
