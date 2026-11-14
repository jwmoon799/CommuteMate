'use strict';
/** World-space two-lane route; signal direction is authoritative and IDs reject stale finishes. */
class TurnRoute {
 constructor(){this.reset();}
 reset(){this.origin={x:0,z:0,h:0};this.s=0;this.lane=1;this.fromLane=1;this.laneX=-2.9;this.bend=0;this.active=null;this.elapsed=0;this.done=false;}
 center(s){
  let x=0,z=-s,h=0;const R=24,lead=40,end=lead+R*Math.PI/2;
  if(this.bend&&s>lead){let a=Math.min((s-lead)/R,Math.PI/2);h=this.bend*a;x=this.bend*R*(1-Math.cos(a));z=-lead-R*Math.sin(a);if(s>end){x+=Math.sin(h)*(s-end);z-=Math.cos(h)*(s-end);}}
  let c=Math.cos(this.origin.h),sn=Math.sin(this.origin.h);
  return{x:this.origin.x+x*c-z*sn,z:this.origin.z+x*sn+z*c,h:h+this.origin.h};
 }
 pose(){const p=this.center(this.s);return{x:p.x+Math.cos(p.h)*this.laneX,z:p.z+Math.sin(p.h)*this.laneX,h:p.h};}
 begin(direction,id){
  if(![-1,1].includes(direction)||!id||this.active===id)return false;
  this.origin=this.center(this.s);this.s=0;this.bend=direction;this.fromLane=this.lane;this.fromX=this.laneX;this.targetLane=direction===-1?1:2;this.active=id;this.elapsed=0;this.done=false;return true;
 }
 signalOff(){this.active=null;this.done=false;}
 advance(dt,speed,running){
  if(this.active&&!this.done){
   this.elapsed=Math.min(9,this.elapsed+Math.max(0,Math.min(dt,.1)));
   let t=this.elapsed,u=Math.min(t/2,1),smooth=u*u*(3-2*u);
   this.laneX=(this.fromX*(1-smooth)+(this.targetLane===1?-2.9:2.9)*smooth);
   this.lane=this.targetLane;
   this.s=t<2?6*t:t<5?12+(t-2)*28/3:40+(t-5)/4*(24*Math.PI/2);
   if(t>=9){this.done=true;return this.active;}
  }else if(!this.active&&running)this.s+=Math.max(0,speed)*dt;
  return null;
 }
 stage(){if(!this.active)return '직진';if(this.done)return '회전 완료 · OFF 수신 확인 중';return this.elapsed<2?(this.fromLane===this.targetLane?'회전 차선 유지':'회전 차선으로 이동'):this.elapsed<5?'코너 진입 준비':this.bend<0?'좌회전 중':'우회전 중';}
}
if(typeof module!=='undefined')module.exports=TurnRoute;
