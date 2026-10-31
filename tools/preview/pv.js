const { chromium } = require('playwright');
const shaders = require('./shaders.json');
const fs = require('fs');
const holeNum = process.argv[2], mode = process.argv[3] || 'tee';
const W = +(process.argv[4] || 1280), HT = +(process.argv[5] || 600); const outFile = process.argv[6] || `pv_hole${holeNum}_${mode}.png`;
const charName = process.argv[7] || 'Casual';
const AS = process.env.ASSETS || '../../app/src/main/assets/';
const b64 = f => fs.readFileSync(f).toString('base64');
const assets = { models: {}, tex: {}, chars: {} };
for (const f of fs.readdirSync(AS + 'models')) { if (f.endsWith('.gltf')) assets.models[f.slice(0, -5)] = { j: fs.readFileSync(AS + 'models/' + f, 'utf8'), b: b64(AS + 'models/' + f.replace('.gltf', '.bin')) }; else if (/\.(png|jpg)$/.test(f)) assets.tex[f] = [f.endsWith('png') ? 'image/png' : 'image/jpeg', b64(AS + 'models/' + f)]; }
assets.chars[charName] = { j: fs.readFileSync(AS + `characters/${charName}.gltf`, 'utf8'), b: b64(AS + `characters/${charName}.bin`) };
const treeMeshes = JSON.parse(fs.readFileSync('trees.json'));
const hole = JSON.parse(fs.readFileSync(`hole${holeNum}.json`));
(async () => {
  const b = await chromium.launch({ args: ['--use-gl=swiftshader','--enable-unsafe-swiftshader','--ignore-gpu-blocklist'] });
  const p = await b.newPage({ viewport: { width: W, height: HT } });
  await p.setContent(`<body style="margin:0"><canvas id=c width=${W} height=${HT}></canvas></body>`);
  p.on('console', m => console.log('page:', m.text()));
  const err = await p.evaluate(async ({ sh, treeMeshes, hole, mode, assets, W, HT, charName, process_cam, FOV }) => {
    const gl = document.getElementById('c').getContext('webgl2', { antialias: true, preserveDrawingBuffer: true });
    const mat = {
      ident: () => [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1],
      mul: (a, b) => { const o = new Array(16).fill(0); for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) for (let k = 0; k < 4; k++) o[c*4+r] += a[k*4+r] * b[c*4+k]; return o; },
      persp: (fov, asp, n, f) => { const t = 1 / Math.tan(fov * Math.PI / 360); return [t/asp,0,0,0, 0,t,0,0, 0,0,(f+n)/(n-f),-1, 0,0,2*f*n/(n-f),0]; },
      look: (e, c, u) => { let z = [e[0]-c[0], e[1]-c[1], e[2]-c[2]]; let l = Math.hypot(...z); z = z.map(v => v/l);
        let x = [u[1]*z[2]-u[2]*z[1], u[2]*z[0]-u[0]*z[2], u[0]*z[1]-u[1]*z[0]]; l = Math.hypot(...x); x = x.map(v => v/l);
        const y = [z[1]*x[2]-z[2]*x[1], z[2]*x[0]-z[0]*x[2], z[0]*x[1]-z[1]*x[0]];
        return [x[0],y[0],z[0],0, x[1],y[1],z[1],0, x[2],y[2],z[2],0, -(x[0]*e[0]+x[1]*e[1]+x[2]*e[2]), -(y[0]*e[0]+y[1]*e[1]+y[2]*e[2]), -(z[0]*e[0]+z[1]*e[1]+z[2]*e[2]), 1]; },
      inv: (m) => { const inv = new Array(16); const a = m;
        inv[0]=a[5]*a[10]*a[15]-a[5]*a[11]*a[14]-a[9]*a[6]*a[15]+a[9]*a[7]*a[14]+a[13]*a[6]*a[11]-a[13]*a[7]*a[10];
        inv[4]=-a[4]*a[10]*a[15]+a[4]*a[11]*a[14]+a[8]*a[6]*a[15]-a[8]*a[7]*a[14]-a[12]*a[6]*a[11]+a[12]*a[7]*a[10];
        inv[8]=a[4]*a[9]*a[15]-a[4]*a[11]*a[13]-a[8]*a[5]*a[15]+a[8]*a[7]*a[13]+a[12]*a[5]*a[11]-a[12]*a[7]*a[9];
        inv[12]=-a[4]*a[9]*a[14]+a[4]*a[10]*a[13]+a[8]*a[5]*a[14]-a[8]*a[6]*a[13]-a[12]*a[5]*a[10]+a[12]*a[6]*a[9];
        inv[1]=-a[1]*a[10]*a[15]+a[1]*a[11]*a[14]+a[9]*a[2]*a[15]-a[9]*a[3]*a[14]-a[13]*a[2]*a[11]+a[13]*a[3]*a[10];
        inv[5]=a[0]*a[10]*a[15]-a[0]*a[11]*a[14]-a[8]*a[2]*a[15]+a[8]*a[3]*a[14]+a[12]*a[2]*a[11]-a[12]*a[3]*a[10];
        inv[9]=-a[0]*a[9]*a[15]+a[0]*a[11]*a[13]+a[8]*a[1]*a[15]-a[8]*a[3]*a[13]-a[12]*a[1]*a[11]+a[12]*a[3]*a[9];
        inv[13]=a[0]*a[9]*a[14]-a[0]*a[10]*a[13]-a[8]*a[1]*a[14]+a[8]*a[2]*a[13]+a[12]*a[1]*a[10]-a[12]*a[2]*a[9];
        inv[2]=a[1]*a[6]*a[15]-a[1]*a[7]*a[14]-a[5]*a[2]*a[15]+a[5]*a[3]*a[14]+a[13]*a[2]*a[7]-a[13]*a[3]*a[6];
        inv[6]=-a[0]*a[6]*a[15]+a[0]*a[7]*a[14]+a[4]*a[2]*a[15]-a[4]*a[3]*a[14]-a[12]*a[2]*a[7]+a[12]*a[3]*a[6];
        inv[10]=a[0]*a[5]*a[15]-a[0]*a[7]*a[13]-a[4]*a[1]*a[15]+a[4]*a[3]*a[13]+a[12]*a[1]*a[7]-a[12]*a[3]*a[5];
        inv[14]=-a[0]*a[5]*a[14]+a[0]*a[6]*a[13]+a[4]*a[1]*a[14]-a[4]*a[2]*a[13]-a[12]*a[1]*a[6]+a[12]*a[2]*a[5];
        inv[3]=-a[1]*a[6]*a[11]+a[1]*a[7]*a[10]+a[5]*a[2]*a[11]-a[5]*a[3]*a[10]-a[9]*a[2]*a[7]+a[9]*a[3]*a[6];
        inv[7]=a[0]*a[6]*a[11]-a[0]*a[7]*a[10]-a[4]*a[2]*a[11]+a[4]*a[3]*a[10]+a[8]*a[2]*a[7]-a[8]*a[3]*a[6];
        inv[11]=-a[0]*a[5]*a[11]+a[0]*a[7]*a[9]+a[4]*a[1]*a[11]-a[4]*a[3]*a[9]-a[8]*a[1]*a[11]+a[8]*a[3]*a[5];
        inv[15]=a[0]*a[5]*a[10]-a[0]*a[6]*a[9]-a[4]*a[1]*a[10]+a[4]*a[2]*a[9]+a[8]*a[1]*a[6]-a[8]*a[2]*a[5];
        let det = a[0]*inv[0]+a[1]*inv[4]+a[2]*inv[8]+a[3]*inv[12]; return inv.map(v => v/det); },
      trs: (x,y,z,sx,sy,sz) => [sx,0,0,0, 0,sy,0,0, 0,0,sz,0, x,y,z,1],
    };
    const prog = (v, f) => { const pr = gl.createProgram(); for (const [n,t] of [[v,gl.VERTEX_SHADER],[f,gl.FRAGMENT_SHADER]]) { const s = gl.createShader(t); gl.shaderSource(s, sh[n]); gl.compileShader(s); if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(n + gl.getShaderInfoLog(s)); gl.attachShader(pr, s);} gl.linkProgram(pr); return pr; };
    const sky = prog('Sky.kt:VERT','Sky.kt:FRAG'), ter = prog('TerrainRender.kt:VERT','TerrainRender.kt:FRAG'), main = prog('GameRenderer.kt:VERTEX_SRC','GameRenderer.kt:FRAGMENT_SRC');
    const U = (pr, n) => gl.getUniformLocation(pr, n);

    // terrain mesh like TerrainRender
    const {x0,z0,cell,nx,nz} = hole; const H = hole.h;
    const V = []; const I = [];
    const hAt = (x,z) => { const fx = Math.min(Math.max((x-x0)/cell,0),nx-1.001), fz = Math.min(Math.max((z-z0)/cell,0),nz-1.001); const ix=Math.floor(fx), iz=Math.floor(fz), tx=fx-ix, tz=fz-iz;
      const g=(i,j)=>H[j*nx+i]; return (g(ix,iz)*(1-tx)+g(ix+1,iz)*tx)*(1-tz)+(g(ix,iz+1)*(1-tx)+g(ix+1,iz+1)*tx)*tz; };
    for (let iz=0; iz<nz; iz++) for (let ix=0; ix<nx; ix++) { const x=x0+ix*cell, z=z0+iz*cell; const gx=(H[iz*nx+Math.min(ix+1,nx-1)]-H[iz*nx+Math.max(ix-1,0)])/(cell*(ix==0||ix==nx-1?1:2)); const gz=(H[Math.min(iz+1,nz-1)*nx+ix]-H[Math.max(iz-1,0)*nx+ix])/(cell*(iz==0||iz==nz-1?1:2)); const l=Math.hypot(gx,1,gz); V.push(x,H[iz*nx+ix],z,-gx/l,1/l,-gz/l); }
    for (let iz=0; iz<nz-1; iz++) for (let ix=0; ix<nx-1; ix++) { const a=iz*nx+ix,b=a+1,c=a+nx,d=c+1; I.push(a,c,b,b,c,d); }
    const border=[]; for (let ix=0;ix<nx;ix++) border.push(ix); for (let iz=1;iz<nz;iz++) border.push(iz*nx+nx-1); for (let ix=nx-2;ix>=0;ix--) border.push((nz-1)*nx+ix); for (let iz=nz-2;iz>=1;iz--) border.push(iz*nx);
    let avg=0; for (const i of border) avg+=V[i*6+1]; avg/=border.length; const first=V.length/6;
    for (const i of border) { const ix=i%nx, iz=Math.floor(i/nx); const ox=ix==0?-1:ix==nx-1?1:0, oz=iz==0?-1:iz==nz-1?1:0; V.push(V[i*6]+ox*1400, avg, V[i*6+2]+oz*1400, 0,1,0); }
    for (let k=0;k<border.length;k++){ const k2=(k+1)%border.length; I.push(border[k], first+k, border[k2], border[k2], first+k, first+k2); }
    const mkMesh = (verts, idx) => { const vao=gl.createVertexArray(); gl.bindVertexArray(vao); const vb=gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER,vb); gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(verts),gl.STATIC_DRAW); const ib=gl.createBuffer(); gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,ib); gl.bufferData(gl.ELEMENT_ARRAY_BUFFER,new Uint32Array(idx),gl.STATIC_DRAW);
      gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0,3,gl.FLOAT,false,24,0); gl.enableVertexAttribArray(1); gl.vertexAttribPointer(1,3,gl.FLOAT,false,24,12); return {vao, n: idx.length}; };
    const tmesh = mkMesh(V, I);
    // surface texture
    const tw=hole.tw, th=hole.th; const px=new Uint8Array(tw*th*4); for (let i=0;i<tw*th;i++){ const c=hole.surf.charCodeAt(i)-48; px[i*4]=c==1?255:0; px[i*4+1]=c==2?255:0; px[i*4+2]=c==3?255:0; px[i*4+3]=0; }
    const tex=gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D,tex); gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,tw,th,0,gl.RGBA,gl.UNSIGNED_BYTE,px);
    gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR); gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR); gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);

    // simple primitives for trees / basket
    const cyl = (()=>{ const v=[],i=[]; const s=16; for(let k=0;k<=s;k++){ const a=k/s*Math.PI*2, c=Math.cos(a), sn=Math.sin(a); v.push(c,0,sn,c,0,sn, c,1,sn,c,0,sn);} for(let k=0;k<s;k++){ const a=k*2; i.push(a,a+2,a+3,a,a+3,a+1);} return mkMesh(v,i); })();
    const cone = (()=>{ const v=[],i=[]; const s=16; const r=1/Math.SQRT2; for(let k=0;k<=s;k++){ const a=k/s*Math.PI*2, c=Math.cos(a), sn=Math.sin(a); v.push(c,0,sn,c*r,r,sn*r, 0,1,0,c*r,r,sn*r);} for(let k=0;k<s;k++){ const a=k*2; i.push(a,a+2,a+1);} return mkMesh(v,i); })();
    const sph = (()=>{ const v=[],i=[]; const la=10,lo=14; for(let a=0;a<=la;a++){ const th=Math.PI*a/la; for(let b=0;b<=lo;b++){ const ph=2*Math.PI*b/lo; const x=Math.sin(th)*Math.cos(ph),y=Math.cos(th),z=Math.sin(th)*Math.sin(ph); v.push(x,y,z,x,y,z);} } for(let a=0;a<la;a++) for(let b=0;b<lo;b++){ const q=a*(lo+1)+b, r=q+lo+1; i.push(q,q+1,r,q+1,r+1,r);} return mkMesh(v,i); })();

    // camera: like the aiming view
    const path=hole.path; const dx=path[1][0]-path[0][0], dz=path[1][1]-path[0][1]; const dl=Math.hypot(dx,dz); const d=[dx/dl,dz/dl];
    const tee=hole.tee; let eye, look; const [CB,CR,CH,CF,CL]=(process_cam);
    if (mode==='tee') { eye=[tee[0]-d[0]*CB+d[1]*CR, tee[1]+CH, tee[2]-d[1]*CB-d[0]*CR]; look=[tee[0]+d[0]*CF, tee[1]+CL, tee[2]+d[1]*CF]; }
    else { const bk=hole.basket; const ex=bk[0]-path[path.length-2][0], ez=bk[2]-path[path.length-2][1]; const l=Math.hypot(ex,ez); eye=[bk[0]-ex/l*14, bk[1]+3.2, bk[2]-ez/l*14]; look=[bk[0], bk[1]+0.8, bk[2]]; }
    gl.viewport(0,0,W,HT);
    let proj=mat.persp(FOV,W/HT,0.5,700); proj=mat.mul(proj,[-1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1]);
    const view=mat.look(eye,look,[0,1,0]); const vp=mat.mul(proj,view); const invVp=mat.inv(vp);
    const sun=[0.45,0.80,0.40];
    gl.clearColor(0.80,0.89,0.95,1); gl.enable(gl.DEPTH_TEST); gl.depthFunc(gl.LEQUAL); gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);
    // sky
    gl.disable(gl.DEPTH_TEST); gl.depthMask(false); gl.useProgram(sky); gl.uniformMatrix4fv(U(sky,'uInvVP'),false,invVp); gl.uniform3f(U(sky,'uCamPos'),...eye); gl.uniform3f(U(sky,'uSunDir'),...sun); gl.uniform1f(U(sky,'uTime'),3); gl.bindVertexArray(null); gl.drawArrays(gl.TRIANGLES,0,3); gl.depthMask(true); gl.enable(gl.DEPTH_TEST);
    // terrain
    gl.useProgram(ter); gl.uniformMatrix4fv(U(ter,'uVP'),false,vp); gl.uniform3f(U(ter,'uLightDir'),...sun); gl.uniform3f(U(ter,'uCamPos'),...eye); gl.uniform3f(U(ter,'uBasket'),...hole.basket);
    gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D,tex); gl.uniform1i(U(ter,'uSurf'),0); gl.uniform4f(U(ter,'uSurfRect'),x0,z0,1/tw,1/th);
    gl.bindVertexArray(tmesh.vao); gl.drawElements(gl.TRIANGLES,tmesh.n,gl.UNSIGNED_INT,0);
    // objects
    gl.useProgram(main); gl.uniform3f(U(main,'uLightDir'),...sun); gl.uniform3f(U(main,'uCamPos'),...eye); gl.uniform3f(U(main,'uFogColor'),0.80,0.89,0.95); gl.uniform1f(U(main,'uStripe'),0); gl.uniform1f(U(main,'uEmissive'),0);
    const drawM = (m, M, r,g,b,a=1) => { gl.uniformMatrix4fv(U(main,'uMVP'),false,mat.mul(vp,M)); gl.uniformMatrix4fv(U(main,'uModel'),false,M); gl.uniform4f(U(main,'uColor'),r,g,b,a); gl.bindVertexArray(m.vao); gl.drawElements(gl.TRIANGLES,m.n,gl.UNSIGNED_INT,0); };
    // ======== textured static models (port of ModelLibrary / ModelShader)
    const msh = prog('Models.kt:VERT','Models.kt:FRAG');
    const sknp = prog('Skinned.kt:VERT','Skinned.kt:FRAG');
    const dec64 = b => Uint8Array.from(atob(b), c => c.charCodeAt(0));
    const texCache = {};
    const getTex = async (file) => { if (texCache[file] !== undefined) return texCache[file]; const a = assets.tex[file]; if (!a) return (texCache[file] = null);
      const bmp = await createImageBitmap(new Blob([dec64(a[1])], { type: a[0] })); const t = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t);
      gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,bmp); gl.generateMipmap(gl.TEXTURE_2D);
      gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR_MIPMAP_LINEAR); gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.REPEAT); gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.REPEAT); return (texCache[file] = t); };
    const readAcc = (J, bin, i) => { const a = J.accessors[i], v = J.bufferViews[a.bufferView]; const nc = {SCALAR:1,VEC2:2,VEC3:3,VEC4:4,MAT4:16}[a.type];
      const ct = a.componentType, sz = (ct==5126||ct==5125)?4:(ct==5123||ct==5122)?2:1; const start = bin.byteOffset + (v.byteOffset||0) + (a.byteOffset||0); const stride = v.byteStride || nc*sz;
      const dv = new DataView(bin.buffer); const out = new Float32Array(a.count*nc);
      for (let k=0;k<a.count;k++) for (let c=0;c<nc;c++){ const o=start+k*stride+c*sz; out[k*nc+c] = ct==5126?dv.getFloat32(o,true):ct==5125?dv.getUint32(o,true):ct==5123?(a.normalized?dv.getUint16(o,true)/65535:dv.getUint16(o,true)):ct==5121?(a.normalized?dv.getUint8(o)/255:dv.getUint8(o)):dv.getInt16(o,true); }
      return out; };
    const models = {};
    const loadModel = async (name) => { if (models[name] !== undefined) return models[name]; const a = assets.models[name]; if (!a) return (models[name] = null);
      const J = JSON.parse(a.j), bin = dec64(a.b); const nd = (J.nodes||[])[0]||{}; const T = nd.translation||[0,0,0], Q = nd.rotation||[0,0,0,1], S = nd.scale||[1,1,1];
      const rot = (x,y,z) => { const [qx,qy,qz,qw]=Q; const tx=2*(qy*z-qz*y), ty=2*(qz*x-qx*z), tz=2*(qx*y-qy*x); return [x+qw*tx+(qy*tz-qz*ty), y+qw*ty+(qz*tx-qx*tz), z+qw*tz+(qx*ty-qy*tx)]; };
      const parts = []; let height = 0.01;
      for (const mesh of J.meshes) for (const p of mesh.primitives) { const at = p.attributes; const pos = readAcc(J,bin,at.POSITION), nor = readAcc(J,bin,at.NORMAL), uv = at.TEXCOORD_0!==undefined?readAcc(J,bin,at.TEXCOORD_0):new Float32Array(pos.length/3*2);
        const idx = Uint16Array.from(readAcc(J,bin,p.indices)); const n = pos.length/3; const vb = new Float32Array(n*8);
        for (let i=0;i<n;i++){ const r = rot(pos[i*3]*S[0],pos[i*3+1]*S[1],pos[i*3+2]*S[2]), rn = rot(nor[i*3],nor[i*3+1],nor[i*3+2]); const y=r[1]+T[1]; height=Math.max(height,y);
          vb.set([r[0]+T[0],y,r[2]+T[2],rn[0],rn[1],rn[2],uv[i*2],uv[i*2+1]], i*8); }
        const mat = J.materials[p.material]; const bt = mat.pbrMetallicRoughness && mat.pbrMetallicRoughness.baseColorTexture; const uri = bt ? J.images[J.textures[bt.index].source].uri : ''; const tex = bt ? await getTex(uri) : null;
        const vao = gl.createVertexArray(); gl.bindVertexArray(vao); const b1 = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER,b1); gl.bufferData(gl.ARRAY_BUFFER,vb,gl.STATIC_DRAW);
        const b2 = gl.createBuffer(); gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,b2); gl.bufferData(gl.ELEMENT_ARRAY_BUFFER,idx,gl.STATIC_DRAW);
        gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0,3,gl.FLOAT,false,32,0); gl.enableVertexAttribArray(1); gl.vertexAttribPointer(1,3,gl.FLOAT,false,32,12); gl.enableVertexAttribArray(2); gl.vertexAttribPointer(2,2,gl.FLOAT,false,32,24);
        parts.push({vao, n: idx.length, tex, leaf: uri.includes('Leaves')}); }
      return (models[name] = { parts, height }); };
    const rotY = (deg) => { const a = deg*Math.PI/180, c=Math.cos(a), s=Math.sin(a); return [c,0,-s,0, 0,1,0,0, s,0,c,0, 0,0,0,1]; };
    const trsM = (x,y,z,ry,sc) => mat.mul(mat.trs(x,y,z,1,1,1), mat.mul(rotY(ry), mat.trs(0,0,0,sc,sc,sc)));
    const clockT = 3;
    const useModelShader = () => { gl.useProgram(msh); gl.uniform3f(U(msh,'uLightDir'),...sun); gl.uniform3f(U(msh,'uCamPos'),...eye); gl.uniform3f(U(msh,'uFogColor'),0.80,0.89,0.95); gl.uniform1f(U(msh,'uTime'),clockT); gl.uniform1i(U(msh,'uTex'),0); gl.activeTexture(gl.TEXTURE0); };
    const LD=[[0.10,0.36,0.09],[0.20,0.42,0.08],[0.66,0.32,0.46]], LL=[[0.52,0.80,0.22],[0.70,0.86,0.28],[1.0,0.78,0.85]];
    const drawModel = (m, M, tint, sway, kind=0) => { gl.uniform3fv(U(msh,'uLeafDark'),LD[kind]); gl.uniform3fv(U(msh,'uLeafLight'),LL[kind]); gl.uniformMatrix4fv(U(msh,'uMVP'),false,mat.mul(vp,M)); gl.uniformMatrix4fv(U(msh,'uModel'),false,M); gl.uniform3f(U(msh,'uTint'),tint,tint,tint);
      gl.uniform1f(U(msh,'uSway'),1/m.height); gl.uniform1f(U(msh,'uLean'),sway); for (const p of m.parts){ gl.uniform1f(U(msh,'uLeaf'),p.leaf?1:0); gl.bindTexture(gl.TEXTURE_2D,p.tex); gl.bindVertexArray(p.vao); gl.drawElements(gl.TRIANGLES,p.n,gl.UNSIGNED_SHORT,0);} };
    const treeNames = ["MapleTree_1","MapleTree_2","MapleTree_3","MapleTree_4","MapleTree_5","BirchTree_1","BirchTree_2","BirchTree_3","BirchTree_4","BirchTree_5"];
    for (const n of new Set([...treeNames, ...hole.decor.map(d=>d[0]), 'Grass_Large','Grass_Small'])) await loadModel(n);
    useModelShader();
    { // ---- puffy trees (port of PuffyTrees + GameRenderer.drawScenery)
      const pt = prog('PuffyTrees.kt:VERT','PuffyTrees.kt:FRAG');
      const tms = treeMeshes.map(m => { const vao=gl.createVertexArray(); gl.bindVertexArray(vao); const b1=gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER,b1); gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(m.v),gl.STATIC_DRAW);
        const b2=gl.createBuffer(); gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,b2); gl.bufferData(gl.ELEMENT_ARRAY_BUFFER,new Uint16Array(m.i),gl.STATIC_DRAW); for(let a=0;a<3;a++){ gl.enableVertexAttribArray(a); gl.vertexAttribPointer(a,3,gl.FLOAT,false,36,a*12);} return {vao,n:m.i.length}; });
      const PLD=[[0.12,0.44,0.15],[0.18,0.40,0.10],[0.62,0.30,0.44]], PLL=[[0.66,0.93,0.28],[0.76,0.90,0.30],[1.0,0.80,0.88]], BD=[[0.30,0.20,0.13],[0.52,0.52,0.50]], BL=[[0.70,0.52,0.34],[0.97,0.96,0.92]];
      gl.useProgram(pt); gl.uniform3f(U(pt,'uLightDir'),...sun); gl.uniform3f(U(pt,'uCamPos'),...eye); gl.uniform3f(U(pt,'uFogColor'),0.80,0.89,0.95); gl.uniform1f(U(pt,'uTime'),clockT);
      hole.trees.forEach((t,idx)=>{ const [x,y0,z,ht,cr,vr]=t; const dx=x-eye[0], dz=z-eye[2]; const d2=dx*dx+dz*dz; if (d2>380*380) return;
        const shape=(idx*7+vr*3)%10; const sc=ht*0.95, wd=Math.min(Math.max(cr/2.3,0.85),1.25); const dist=Math.sqrt(d2); const det=dist<42?2:dist<120?1:0; const tint=0.90+((idx*37)%30)/100;
        const M=mat.mul(mat.trs(x,y0-0.1,z,1,1,1), mat.mul(rotY((idx*83)%360), mat.trs(0,0,0,sc*wd,sc,sc*wd)));
        const kind=idx%9==4?2:idx%5==1?1:0, birch=shape>=5?1:0;
        gl.uniform3fv(U(pt,'uLeafDark'),PLD[kind]); gl.uniform3fv(U(pt,'uLeafLight'),PLL[kind]); gl.uniform3fv(U(pt,'uBarkDark'),BD[birch]); gl.uniform3fv(U(pt,'uBarkLight'),BL[birch]);
        gl.uniformMatrix4fv(U(pt,'uMVP'),false,mat.mul(vp,M)); gl.uniformMatrix4fv(U(pt,'uModel'),false,M); gl.uniform3f(U(pt,'uTint'),tint,tint,tint); gl.uniform1f(U(pt,'uLean'),0.03);
        const m=tms[(shape%6)*3+det]; gl.bindVertexArray(m.vao); gl.drawElements(gl.TRIANGLES,m.n,gl.UNSIGNED_SHORT,0); });
      useModelShader(); }
    for (const d of hole.decor) { const m = models[d[0]]; if(!m) continue; const dx=d[1]-eye[0], dz=d[3]-eye[2]; if (dx*dx+dz*dz>160*160) continue; drawModel(m, trsM(d[1],d[2]-0.02,d[3],d[5],d[4]), d[6], 0.05); }
    // near grass (same hash grid as the game)
    { const cell=1.7, cx=Math.floor(eye[0]/cell), cz=Math.floor(eye[2]/cell); const fx=look[0]-eye[0], fz=look[2]-eye[2], fl=Math.max(Math.hypot(fx,fz),0.01);
      const surfAt = (x,z) => { const i=Math.floor(x-x0), j=Math.floor(z-z0); if(i<0||j<0||i>=tw||j>=th) return 0; return hole.surf.charCodeAt(j*tw+i)-48; };
      for (let ix=cx-10; ix<=cx+10; ix++) for (let iz=cz-10; iz<=cz+10; iz++) { let k = Math.imul(ix,73856093) ^ Math.imul(iz,19349663); k ^= (k>>>13); k = Math.imul(k,1274126177); k ^= (k>>>16);
        if ((k&0xFF)>150) continue; const x=(ix+((k>>8)&0xFF)/255)*cell, z=(iz+((k>>16)&0xFF)/255)*cell; const dx=x-eye[0], dz=z-eye[2];
        if (dx*dx+dz*dz>17*17 || (dx*fx+dz*fz)/fl < -2) continue; const sf=surfAt(x,z); if (sf==1||sf==2) continue;
        if ((x-hole.tee[0])**2+(z-hole.tee[2])**2<9) continue; if ((x-hole.basket[0])**2+(z-hole.basket[2])**2<56) continue;
        const m = models[((k>>>24)&1)==0?'Grass_Large':'Grass_Small']; if(!m) continue; drawM2(m,x,z,k); } }
    function drawM2(m,x,z,k){ drawModel(m, trsM(x,hAt(x,z)-0.02,z,((k>>>20)&0xFF)*1.4,1.1+((k>>>4)&7)*0.09), 0.9+((k>>>10)&15)/60, 0.03); }
    // ======== skinned golfer (port of SkinnedModel / SkinnedGolfer, address pose = Windup clip at power 0)
    { const a = assets.chars[charName]; const J = JSON.parse(a.j), bin = dec64(a.b); const nodes = J.nodes, N = nodes.length; const parent = new Int32Array(N).fill(-1);
      nodes.forEach((nd,i)=> (nd.children||[]).forEach(c=>parent[c]=i));
      const P = { t:new Float32Array(N*3), r:new Float32Array(N*4), s:new Float32Array(N*3) };
      nodes.forEach((nd,i)=>{ for(let k=0;k<3;k++){ P.t[i*3+k]=(nd.translation||[0,0,0])[k]; P.s[i*3+k]=(nd.scale||[1,1,1])[k]; } for(let k=0;k<4;k++) P.r[i*4+k]=(nd.rotation||[0,0,0,1])[k]; });
      const skin = J.skins[0], joints = skin.joints, ibm = readAcc(J,bin,skin.inverseBindMatrices);
      const clips = {}; for (const an of J.animations||[]) { const ch=[]; let t0=1e9,t1=-1e9; for (const c of an.channels){ const path={translation:0,rotation:1,scale:2}[c.target.path]; if(path===undefined) continue; const sm=an.samplers[c.sampler]; const times=readAcc(J,bin,sm.input), values=readAcc(J,bin,sm.output); t0=Math.min(t0,times[0]); t1=Math.max(t1,times[times.length-1]); ch.push({node:c.target.node,path,times,values}); } clips[an.name]={start:t0,duration:Math.max(t1-t0,1e-3),ch}; }
      const sampleClip = (clip,time) => { const o={t:Float32Array.from(P.t),r:Float32Array.from(P.r),s:Float32Array.from(P.s)}; const tt=clip.start+Math.min(Math.max(time,0),clip.duration);
        for (const c of clip.ch){ const ts=c.times; let i=0; while(i<ts.length-2&&tt>ts[i+1]) i++; const u=ts.length<2?0:Math.min(Math.max((tt-ts[i])/Math.max(ts[i+1]-ts[i],1e-5),0),1); const j=Math.min(i+1,ts.length-1), n=c.node;
          if (c.path==1){ let [a0,a1,a2,a3]=[0,1,2,3].map(k=>c.values[i*4+k]); let b=[0,1,2,3].map(k=>c.values[j*4+k]); let dot=a0*b[0]+a1*b[1]+a2*b[2]+a3*b[3]; const sg=dot<0?-1:1; dot*=sg; let ka,kb; if(dot>0.9995){ka=1-u;kb=u*sg;} else {const th=Math.acos(Math.min(dot,1)), sn=Math.sin(th); ka=Math.sin((1-u)*th)/sn; kb=Math.sin(u*th)/sn*sg;} const q=[a0*ka+b[0]*kb,a1*ka+b[1]*kb,a2*ka+b[2]*kb,a3*ka+b[3]*kb]; const l=1/Math.hypot(...q); for(let k=0;k<4;k++) o.r[n*4+k]=q[k]*l; }
          else { const arr=c.path==0?o.t:o.s; for(let k=0;k<3;k++) arr[n*3+k]=c.values[i*3+k]+(c.values[j*3+k]-c.values[i*3+k])*u; } } return o; };
      const pose = sampleClip(clips['Windup_BH'], 0);
      const loc = (n)=>{ const x=pose.r[n*4],y=pose.r[n*4+1],z=pose.r[n*4+2],w=pose.r[n*4+3]; const sx=pose.s[n*3],sy=pose.s[n*3+1],sz=pose.s[n*3+2];
        return [(1-2*(y*y+z*z))*sx,2*(x*y+z*w)*sx,2*(x*z-y*w)*sx,0, 2*(x*y-z*w)*sy,(1-2*(x*x+z*z))*sy,2*(y*z+x*w)*sy,0, 2*(x*z+y*w)*sz,2*(y*z-x*w)*sz,(1-2*(x*x+y*y))*sz,0, pose.t[n*3],pose.t[n*3+1],pose.t[n*3+2],1]; };
      const glob = new Array(N); const solve = (n)=>{ if(glob[n]) return glob[n]; const l=loc(n); return glob[n] = parent[n]>=0 ? mat.mul(solve(parent[n]), l) : l; }; for (let i=0;i<N;i++) solve(i);
      const meshNode = nodes.findIndex(n=>n.mesh!==undefined); const meshInv = mat.inv(glob[meshNode]);
      const rows = new Float32Array(192*4); joints.forEach((jn,j)=>{ const sk = mat.mul(meshInv, mat.mul(glob[jn], Array.from(ibm.slice(j*16,j*16+16)))); for(let r=0;r<3;r++) for(let c=0;c<4;c++) rows[j*12+r*4+c]=sk[c*4+r]; });
      // appearance (defaults) -> colours
      const hex=(c,k=1)=>[((c>>16)&255)/255*k,((c>>8)&255)/255*k,(c&255)/255*k];
      const SKIN=[0xF5D0B0,0xE0AC85,0xC68A5E,0x9C6A44,0x6E4528,0x4A2E1B], HAIR=[0x1B1B1B,0x4A3020,0x8A5A2B,0xC9A04E,0xB8341F,0xC8C8C8,0x2E5BD1,0x2FA84F], SHIRT=[0xE8453C,0x2E86DE,0xF1C40F,0x27AE60,0xECF0F1,0x2C3E50,0xE67E22,0x9B59B6,0xFF6FA5,0x16A085], SHORTS=[0x2C3E50,0x34495E,0xBDC3C7,0x7F5A3A,0x145A32,0xB03A2E];
      const roles={Casual2:[['LightBrown'],['LightBlue'],['Hair']],Casual:[['Purple'],['LightBlue'],['Hair']],Beach:[['LightBrown'],['Red_Dark'],['Hair']],Adventurer:[['Green'],['Brown'],['Hair']],Punk:[['White'],['LightBlue'],['Red','Red_Dark']],Worker:[[],['Brown'],['Hair']]}[charName];
      const skinC=hex(SKIN[1]), skinD=hex(SKIN[1],0.85), hairC=hex(HAIR[1]), brow=hex(HAIR[1],0.6), shirtC=hex(SHIRT[2]), shortsC=hex(SHORTS[1]);
      gl.useProgram(sknp); gl.uniform3f(U(sknp,'uLightDir'),...sun); gl.uniform3f(U(sknp,'uCamPos'),...eye); gl.uniform3f(U(sknp,'uFogColor'),0.80,0.89,0.95); gl.uniform4fv(U(sknp,'uBones'), rows.subarray(0,64*12));
      const path=hole.path; const hd=Math.atan2(path[1][0]-path[0][0], path[1][1]-path[0][1])*180/Math.PI; const bw=1;
      const world = mat.mul(mat.trs(hole.tee[0],hole.tee[1],hole.tee[2],1,1,1), mat.mul(rotY(hd), mat.trs(0,0,0,-bw,1,bw)));
      gl.uniformMatrix4fv(U(sknp,'uMVP'),false,mat.mul(vp,world)); gl.uniformMatrix4fv(U(sknp,'uModel'),false,world);
      const parts=[]; for (const mesh of J.meshes) for (const p of mesh.primitives) { const at=p.attributes; const pos=readAcc(J,bin,at.POSITION), nor0=readAcc(J,bin,at.NORMAL), jn=readAcc(J,bin,at.JOINTS_0), wt=readAcc(J,bin,at.WEIGHTS_0); const idx=Uint16Array.from(readAcc(J,bin,p.indices)); const n=pos.length/3;
        const nor=new Float32Array(n*3); { const M=new Map(); const ky=i=>[0,1,2].map(k=>Math.round(pos[i*3+k]*400)).join(','); for(let i=0;i<n;i++){ const k=ky(i); let a=M.get(k); if(!a){a=[0,0,0];M.set(k,a);} for(let c=0;c<3;c++) a[c]+=nor0[i*3+c]; } for(let i=0;i<n;i++){ const a=M.get(ky(i)); const l=1/Math.max(Math.hypot(a[0],a[1],a[2]),1e-4); for(let c=0;c<3;c++) nor[i*3+c]=nor0[i*3+c]*0.15+a[c]*l*0.85; } }
        const vb=new Float32Array(n*14); for(let i=0;i<n;i++){ vb.set(pos.subarray(i*3,i*3+3),i*14); vb.set(nor.subarray(i*3,i*3+3),i*14+3); vb.set(jn.subarray(i*4,i*4+4),i*14+6); vb.set(wt.subarray(i*4,i*4+4),i*14+10); }
        const vao=gl.createVertexArray(); gl.bindVertexArray(vao); const b1=gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER,b1); gl.bufferData(gl.ARRAY_BUFFER,vb,gl.STATIC_DRAW); const b2=gl.createBuffer(); gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,b2); gl.bufferData(gl.ELEMENT_ARRAY_BUFFER,idx,gl.STATIC_DRAW);
        gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0,3,gl.FLOAT,false,56,0); gl.enableVertexAttribArray(1); gl.vertexAttribPointer(1,3,gl.FLOAT,false,56,12); gl.enableVertexAttribArray(2); gl.vertexAttribPointer(2,4,gl.FLOAT,false,56,24); gl.enableVertexAttribArray(3); gl.vertexAttribPointer(3,4,gl.FLOAT,false,56,40);
        const mt=J.materials[p.material]; const bc=(mt.pbrMetallicRoughness||{}).baseColorFactor||[.8,.8,.8]; let col=[0,1,2].map(k=>Math.pow(Math.min(Math.max(bc[k],0),1),1/2.2)); const nm=mt.name||'';
        if (nm=='Skin') col=skinC; else if (nm=='Skin_Darker') col=skinD; else if (nm=='Eyebrows') col=brow; else if (roles[2].includes(nm)) col=hairC; else if (roles[0].includes(nm)) col=shirtC; else if (roles[1].includes(nm)) col=shortsC;
        const kind = nm.startsWith('Skin')?2:(nm=='Hair'||nm=='Eyebrows')?3:nm=='Eye'?4:(roles[0].includes(nm)||roles[1].includes(nm)||roles[2].includes(nm))?1:0; parts.push({vao,n:idx.length,col,kind}); }
      gl.enable(gl.CULL_FACE); gl.cullFace(gl.FRONT); gl.uniform1f(U(sknp,'uOutline'),0.020); for (const q of parts){ gl.bindVertexArray(q.vao); gl.drawElements(gl.TRIANGLES,q.n,gl.UNSIGNED_SHORT,0);} gl.disable(gl.CULL_FACE); gl.uniform1f(U(sknp,'uOutline'),0);
      for (const q of parts){ gl.uniform1f(U(sknp,'uKind'),q.kind); gl.uniform3f(U(sknp,'uColor'),...q.col); gl.bindVertexArray(q.vao); gl.drawElements(gl.TRIANGLES,q.n,gl.UNSIGNED_SHORT,0);}

      // ---- held disc (same maths as SkinnedGolfer + GameRenderer): puck lathe mesh, in the right hand
      { const prof=[[0,.26],[.22,.27],[.45,.285],[.68,.30],[.84,.305],[.93,.29],[.985,.24],[1,.17],[.99,.10],[.955,.055],[.90,.045],[.84,.075],[.62,.11],[.30,.14],[0,.15]]; const n=prof.length, seg=56; const nr=[],ny=[];
        for(let j=0;j<n;j++){ const a=prof[Math.max(j-1,0)], c=prof[Math.min(j+1,n-1)]; const tr=c[0]-a[0], ty=c[1]-a[1]; const l=Math.max(Math.hypot(tr,ty),1e-5); nr.push(-ty/l); ny.push(tr/l); } nr[0]=0;ny[0]=1;nr[n-1]=0;ny[n-1]=-1;
        const V=[],I=[]; for(let k=0;k<=seg;k++){ const a=k/seg*Math.PI*2,c=Math.cos(a),s=Math.sin(a); for(let j=0;j<n;j++) V.push(prof[j][0]*c,prof[j][1]-0.2,prof[j][0]*s,nr[j]*c,ny[j],nr[j]*s); }
        for(let k=0;k<seg;k++) for(let j=0;j<n-1;j++){ const a=k*n+j,b=(k+1)*n+j; I.push(a,j+1+k*n,j+1+(k+1)*n,a,j+1+(k+1)*n,b); }
        const puck=mkMesh(V,I);
        const wi=nodes.findIndex(x=>x.name=='Wrist.R'), fi=nodes.findIndex(x=>x.name=='LowerArm.R');
        const wp=[glob[wi][12],glob[wi][13],glob[wi][14]], ep=[glob[fi][12],glob[fi][13],glob[fi][14]]; let dx=wp[0]-ep[0],dy=wp[1]-ep[1],dz=wp[2]-ep[2]; const dl=Math.hypot(dx,dy,dz); dx/=dl;dy/=dl;dz/=dl;
        const cx=wp[0]+dx*0.10, cy=wp[1]+dy*0.10+0.02, cz=wp[2]+dz*0.10; let fx=dx,fz=dz; const fl=Math.hypot(fx,fz); if(fl<1e-3){fx=0;fz=1;}else{fx/=fl;fz/=fl;}
        const basis=[fz,0,-fx,0, 0,1,0,0, fx,0,fz,0, cx,cy,cz,1];
        const M=mat.mul(world, mat.mul(basis, mat.trs(0,0,0,0.105,0.105,0.105)));
        gl.useProgram(main); gl.uniform3f(U(main,'uLightDir'),...sun); gl.uniform3f(U(main,'uCamPos'),...eye); gl.uniform3f(U(main,'uFogColor'),0.80,0.89,0.95); gl.uniform1f(U(main,'uStripe'),0); gl.uniform1f(U(main,'uEmissive'),0);
        gl.uniformMatrix4fv(U(main,'uMVP'),false,mat.mul(vp,M)); gl.uniformMatrix4fv(U(main,'uModel'),false,M); gl.uniform4f(U(main,'uColor'),0.18,0.53,0.87,1); gl.bindVertexArray(puck.vao); gl.drawElements(gl.TRIANGLES,puck.n,gl.UNSIGNED_INT,0);
        console.log('hand', wp, 'disc', cx, cy, cz); }
    }

    gl.useProgram(main); gl.uniform3f(U(main,'uLightDir'),...sun); gl.uniform3f(U(main,'uCamPos'),...eye); gl.uniform3f(U(main,'uFogColor'),0.80,0.89,0.95); gl.uniform1f(U(main,'uStripe'),0); gl.uniform1f(U(main,'uEmissive'),0);
    const bk=hole.basket; drawM(cyl, mat.trs(bk[0],bk[1],bk[2],0.035,1.4,0.035), 0.7,0.72,0.76); drawM(cyl, mat.trs(bk[0]+0.7,bk[1],bk[2],0.02,3.4,0.02), 0.9,0.9,0.9); drawM(cyl, mat.trs(bk[0],bk[1]+0.55,bk[2],0.3,0.4,0.3), 0.6,0.62,0.66);
    // water
    gl.enable(gl.BLEND); gl.blendFuncSeparate(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA,gl.ZERO,gl.ONE); gl.depthMask(false);
    const disc = (()=>{ const v=[0,0,0,0,1,0], i=[]; const s=40; for(let k=0;k<s;k++){ const a=k/s*Math.PI*2; v.push(Math.cos(a),0,Math.sin(a),0,1,0);} for(let k=0;k<s;k++) i.push(0,1+(k+1)%s,1+k); return mkMesh(v,i); })();
    for (const [cx,cz,rx,rz,lv] of hole.ponds) { drawM(disc, mat.trs(cx,lv+0.03,cz,rx*1.02,1,rz*1.02),0.10,0.40,0.62,0.55); drawM(disc, mat.trs(cx,lv+0.04,cz,rx*0.97,1,rz*0.97),0.12,0.46,0.70,0.78); drawM(disc, mat.trs(cx,lv+0.05,cz,rx*0.70,1,rz*0.70),0.20,0.56,0.80,0.40); }
    { const t=hole.tee, hd2=0; const g=(r,a,c)=>drawM(disc, mat.trs(t[0],t[1]+0.05,t[2],r,1,r),c[0],c[1],c[2],a); for(let i=0;i<12;i++){ g(1.2-i*0.065,0.042,[0,.06,.02]); } }
    return gl.getError();
  }, { sh: shaders, treeMeshes, hole, mode, assets, W, HT, charName, process_cam: (process.env.CAM||'3.6,0.7,1.5,14,0.9').split(',').map(Number), FOV: +(process.env.FOV||50) });
  console.log('gl error', err);
  await p.screenshot({ path: outFile });
  await b.close();
})();
