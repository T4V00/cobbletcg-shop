const fs=require('fs'),vm=require('vm'),path=require('path');
const ctx={console};ctx.globalThis=ctx;vm.createContext(ctx);
vm.runInContext(fs.readFileSync(path.join(__dirname,'..','src/main/resources/assets/tcgshop/ptcg/engine.js'),'utf8'),ctx);
const P=ctx.PTCG;P.init();
const stats={}, kinds={};
for (let g=0; g<Number(process.argv[2]||6); g++){
  const d1=P.buildDeck('',300+g), d2=P.buildDeck('',400+g);
  P.create('g',JSON.stringify({id:1,name:'H',deck:JSON.parse(d1),bot:false}),JSON.stringify({id:2,name:'B',deck:JSON.parse(d2),bot:true}));
  // the human uses twinleaf's bot to decide, but we record the prompts
  const P2 = P; let steps=0;
  while(steps++<3000){
    if (P.botStep('g')) continue;
    const v=JSON.parse(P.view('g',1));
    if (v.phase===7||v.winner!==-1) break;
    if (v.prompt){ stats[v.prompt.type]=(stats[v.prompt.type]||0)+1; kinds[v.prompt.kind]=(kinds[v.prompt.kind]||0)+1; P.auto('g',1); }
    else if (v.myTurn){ P.act('g',1,JSON.stringify({t:'pass'})); }
  }
  P.dispose('g');
}
console.log(JSON.stringify(stats,null,1)); console.log(JSON.stringify(kinds));
