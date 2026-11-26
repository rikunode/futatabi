// Exercise exactly the Java-generated handoff scripts against a synthetic form.
// No WebView login, network, Turnstile solving, or POST.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const scripts = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const events = [], files = [], previews = [], tags = [{name:'tags[0].name',value:''}];
let invalid=false, disabled=true;
const submit={textContent:'スレッドを作成する',matches:q=>q===':disabled'&&disabled};
const form={
  querySelectorAll(q){return {'img[alt]':previews,'button[type=submit]':[submit],'input[name]':tags}[q]||[]},
  querySelector(q){return q==='[aria-invalid=true]'&&invalid?{}:null}
};
let dropped;
class Input {
  get value(){return this._value || ''}
  set value(v){this._value=v}
  closest(q){return q==='form'?form:null}
  dispatchEvent(e){events.push(e.type)}
}
class Textarea extends Input {
  get value(){return super.value}
  set value(v){super.value=v}
  closest(q){return q==='section'?section:super.closest(q)}
}
const section = {dispatchEvent(e){dropped=e;return true}};
const title = new Input(), body = new Textarea();
const location = {origin:'https://mebuki.moe', pathname:'/app/new', search:''};
const stored = new Map(), dispatched = [];
const window = {dispatchEvent:e=>dispatched.push(e)};
const localStorage = {setItem:(key,value)=>stored.set(key,value),getItem:key=>stored.get(key)??null};
class File {
  constructor(parts,name,options){this.bytes=Buffer.concat(parts.map(x=>Buffer.from(x)));this.name=name;this.type=options.type}
}
class DataTransfer {constructor(){this.items={add:f=>files.push(f)}}}
class Event {constructor(type,options){this.type=type;Object.assign(this,options)}}
const ctx = vm.createContext({location,window,localStorage,CustomEvent:Event,HTMLInputElement:Input,HTMLTextAreaElement:Textarea,DataTransfer,File,Event,DragEvent:Event,atob,
  document:{querySelector(q){return {'input[name=name]':title,'textarea[name=content]':body,'input[name=attachments]':{}}[q]||null}}});
const run = name => vm.runInContext(scripts[name],ctx);
location.origin='https://example.invalid';
for(const step of ['ready','begin','chunk','finish'])assert.equal(run(step),false);
assert.equal(title.value,'');assert.equal(files.length,0);
location.origin='https://mebuki.moe';
location.pathname='/app/t/fixture123';
assert.equal(run('begin'),false);
location.pathname='/app/new';location.search='?fixture=1';
assert.equal(run('begin'),false);
location.search='';
assert.equal(run('ready'),true);
assert.equal(run('begin'),true);
assert.equal(title.value,'fixture');assert.equal(body.value,'title');
assert.equal(run('banner'),true);assert.equal(stored.get('pwa-install-dismissed'),'true');assert.equal(dispatched[0].type,'local-storage-change');assert.equal(dispatched[0].detail.value,true);
assert.equal(run('banner'),true);assert.equal(dispatched.length,1,'already dismissed: no React event');
location.origin='https://example.invalid';assert.equal(run('banner'),false);assert.equal(dispatched.length,1);location.origin='https://mebuki.moe';
assert.deepEqual(events,['input','change','input','change']);
assert.equal(run('imageReady'),false);
assert.equal(run('observe').disabled,true);
assert.equal(run('observe').tagMissing,true);
tags[0].value='fixture-tag';invalid=true;disabled=false;
assert.equal(run('observe').disabled,false);assert.equal(run('observe').tagMissing,false);assert.equal(run('observe').invalid,true);
assert.equal(run('chunk'),true);
assert.equal(run('finish'),true);
assert.equal(files.length,1);assert.equal(files[0].name,'attachment.jpg');assert.equal(files[0].type,'image/jpeg');
assert.equal(files[0].bytes.toString(),'abc');assert.equal(dropped.type,'drop');
assert.equal(window['fixture-draft'],undefined);assert.equal(run('finish'),false);
assert.equal(run('imageReady'),false,'dispatch alone does not confirm attachment');
previews.push({alt:'unrelated.jpg'});assert.equal(run('imageReady'),false);
previews.push({alt:'attachment.jpg'});assert.equal(run('imageReady'),true);
title.value='';body.value='';events.length=0;
assert.equal(run('beginEmpty'),true);assert.equal(title.value,'fixture');assert.equal(body.value,'');
assert.deepEqual(events,['input','change'],'no empty-body validation event');
location.origin='https://example.invalid';
assert.equal(run('imageReady'),false);assert.equal(run('observe'),false);
console.log('PASS official-form scripts: origin, title/body, no redundant input, one drop, actual preview gate, idempotent banner, read-only disabled/tag observation, no submission');
