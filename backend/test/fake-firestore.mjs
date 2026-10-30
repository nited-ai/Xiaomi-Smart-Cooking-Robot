// Small transactional Firestore double. It enforces all reads before writes,
// serializes transactions, and rolls back on thrown errors. It is not a cloud test.
import { strict as assert } from 'node:assert';
export class FakeFirestore {
  docs = new Map();
  tail = Promise.resolve();
  collection(path) { return new Collection(this, path); }
  async runTransaction(action) {
    const previous = this.tail;
    let release; this.tail = new Promise(resolve => { release = resolve; });
    await previous;
    const writes = []; let mutated = false;
    const tx = {
      get: async ref => { assert.equal(mutated, false, 'Firestore forbids read after write'); return snapshot(this.docs.get(ref.path)); },
      create: (ref, value) => { mutated = true; assert.ok(!this.docs.has(ref.path)); writes.push(() => this.docs.set(ref.path, structuredClone(value))); },
      set: (ref, value) => { mutated = true; writes.push(() => this.docs.set(ref.path, structuredClone(value))); },
      update: (ref, value) => { mutated = true; assert.ok(this.docs.has(ref.path)); writes.push(() => this.docs.set(ref.path, { ...this.docs.get(ref.path), ...structuredClone(value) })); },
      delete: ref => { mutated = true; writes.push(() => this.docs.delete(ref.path)); }
    };
    try { const value = await action(tx); writes.forEach(write => write()); return value; }
    finally { release(); }
  }
}
const snapshot = value => ({ exists: value !== undefined, data: () => structuredClone(value) });
class Document {
  constructor(db, path) { Object.assign(this, { db, path }); }
  collection(name) { return new Collection(this.db, `${this.path}/${name}`); }
  async create(value) { assert.ok(!this.db.docs.has(this.path)); this.db.docs.set(this.path, structuredClone(value)); }
}
class Collection {
  constructor(db, path) { Object.assign(this, { db, path, max: Infinity }); }
  doc(name) { return new Document(this.db, `${this.path}/${name}`); }
  orderBy(field, direction) { this.field = field; this.direction = direction; return this; }
  limit(max) { this.max = max; return this; }
  async get() {
    let docs = [...this.db.docs.entries()].filter(([key]) => key.startsWith(this.path + '/') && !key.slice(this.path.length + 1).includes('/'));
    if (this.field) docs.sort((a,b) => (a[1][this.field] - b[1][this.field]) * (this.direction === 'desc' ? -1 : 1));
    return { docs: docs.slice(0,this.max).map(([path,value]) => ({ id: path.split('/').at(-1), ...snapshot(value) })) };
  }
}
