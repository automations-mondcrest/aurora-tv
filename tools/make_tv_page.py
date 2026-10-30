"""Turn the published preview (aurora.tpl.html) into the TV build's assets/index.html:
bundled fonts, photos as asset files, CSS that old Android WebViews understand,
and the native bridge (installed apps, inputs, Back/Menu/Home keys)."""
import json, re, sys, os

base = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
t = open(os.path.join(base, 'web', 'aurora.tpl.html')).read()

def rep(a, b, count=1):
    global t
    n = t.count(a)
    assert n == count, (n, a[:70])
    t = t.replace(a, b)

# ---- page shell -------------------------------------------------------------
title_end = t.index('<style>')
head = ('<!doctype html><html><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width,initial-scale=1">')
t = head + t[:title_end].replace('<meta name="claude:viewport-fit-sides" content="cover">', '') + t[title_end:]
t = re.sub(r'<link rel="preconnect"[^>]*>\s*', '', t)
t = re.sub(r'<link rel="stylesheet" href="https://fonts.googleapis.com[^>]*>\s*', '', t)
fonts = '''
  @font-face{font-family:"Outfit";font-weight:500;src:url(fonts/outfit-500.woff2) format("woff2")}
  @font-face{font-family:"Outfit";font-weight:400;src:url(fonts/outfit-500.woff2) format("woff2")}
  @font-face{font-family:"Manrope";font-weight:500;src:url(fonts/manrope-500.woff2) format("woff2")}
  @font-face{font-family:"Manrope";font-weight:600;src:url(fonts/manrope-600.woff2) format("woff2")}
  @font-face{font-family:"Manrope";font-weight:700;src:url(fonts/manrope-700.woff2) format("woff2")}
  @font-face{font-family:"Manrope";font-weight:800;src:url(fonts/manrope-800.woff2) format("woff2")}
'''
t = t.replace('<style>', '<style>' + fonts, 1)

# ---- CSS for older WebViews (no clamp/max/env/inset/aspect-ratio/flex gap) ----
css_start = t.index('<style>'); css_end = t.index('</style>')
css = t[css_start:css_end]
css = re.sub(r'max\(\s*([^,()]+?)\s*,\s*env\([^()]*\)\s*\)', r'\1', css)
css = re.sub(r'env\(safe-area-inset-[a-z]+\s*,\s*0px\)', '0px', css)
css = re.sub(r'clamp\(\s*([^,()]+)\s*,\s*([^,()]+)\s*,\s*([^,()]+)\s*\)', r'\2', css)
css = re.sub(r'calc\(50% - ([0-9.]+vh)/2 - 8px\)', r'calc(50% - \1 / 2 - 8px)', css)
css = re.sub(r'inset:0;', 'top:0;right:0;bottom:0;left:0;', css)
css = re.sub(r'inset:0}', 'top:0;right:0;bottom:0;left:0}', css)
assert 'inset:' not in css and 'clamp(' not in css and 'env(' not in css, re.findall(r'(inset:[^;}]*|clamp\([^)]*\)|env\([^)]*\))', css)
t = t[:css_start] + css + t[css_end:]

tv_css = '''
  /* ---- TV build: margins instead of flex gap, explicit icon heights ---- */
  .dock,.sysrow,.pill,.app,.libhead,.hint,.status,.pop,.mi,.veil .card,.dockwrap,.chips{gap:0!important}
  .dock>.app{margin:0 1.1vw}
  .sysrow>.app{margin:6px .7vw}
  .dockwrap>.dock{margin-bottom:1.8vh}
  .app .ico{margin-bottom:8px}
  .pill .ico{margin:0 8px 0 0}
  .ico{height:9.5vh}
  .pill .ico{height:4.4vh}
  .veil .ico{height:14vh}
  .veil .card>*{margin:8px 0}
  .mi svg{margin-right:10px}
  .pop .mi{margin:2px 0}
  .lgrid{grid-gap:2.4vh 1.6vw}
  .libhead h2{margin-right:16px}
  .lib .sect{margin-top:1.6vh}
  .ico img{width:100%;height:100%;object-fit:cover;display:block;position:relative;z-index:0}
  .ico.native{background:#1c1c24}
  .hint .key,.hint button{margin-right:4px}
  .hint{transition:opacity 1s}
  .hint.gone{opacity:0}
  #next{display:none}
'''
t = t.replace('</style>', tv_css + '</style>', 1)

# ---- JS: photos from asset files ---------------------------------------------
photos = open(os.path.join(base, 'tools', 'apk_photos.json')).read()
rep('var PHOTOS = __PHOTOS__;', 'var PHOTOS = ' + photos + ';')

# ---- JS: native bridge -------------------------------------------------------
rep("  var BYID={}; APPS.concat(SOURCES).forEach(function(x){BYID[x.id]=x;});\n"
    "  var DEFAULT={apps:['netflix','youtube','dstv','prime','disney','spotify'],sources:['hdmi1','hdmi2']};",
r"""  function srcIcon(type){ if(type===0) return I.antenna; if(type>=1001&&type<=1006) return I.av; return I.hdmi; }
  function loadNative(){
    if(!NATIVE) return;
    try{
      var na=JSON.parse(window.Aurora.apps());
      na.sort(function(a,b){ return a.name.toLowerCase()<b.name.toLowerCase()?-1:1; });
      APPS=na.map(function(a){ return {id:a.pkg,name:a.name,kind:'app',tint:'#1c1c24',native:true,system:!!a.system,
        icon:'<img alt="" src="data:image/png;base64,'+a.icon+'">'}; });
    }catch(e){}
    try{
      var ni=JSON.parse(window.Aurora.inputs());
      SOURCES=ni.map(function(s){ return {id:s.id,name:s.label,kind:'source',pass:!!s.passthrough,icon:srcIcon(s.type)}; });
    }catch(e){ SOURCES=[]; }
    BYID={}; APPS.concat(SOURCES).forEach(function(x){BYID[x.id]=x;});
  }
  var BYID={}; APPS.concat(SOURCES).forEach(function(x){BYID[x.id]=x;});
  var DEFAULT={apps:['netflix','youtube','dstv','prime','disney','spotify'],sources:['hdmi1','hdmi2']};
  if(NATIVE){
    loadNative();
    var want=['com.netflix.ninja','com.netflix.mediaclient','com.google.android.youtube.tv','com.google.android.youtube',
      'com.dstv.now.android','com.multichoice.dstvnow','com.amazon.amazonvideo.livingroom','com.disney.disneyplus','com.spotify.tv.android'];
    var dApps=want.filter(function(p){return BYID[p];});
    APPS.forEach(function(a){ if(dApps.length<6&&dApps.indexOf(a.id)<0&&!/settings|store|market|browser|filemanager/i.test(a.id)) dApps.push(a.id); });
    DEFAULT={apps:dApps.slice(0,6),sources:SOURCES.slice(0,2).map(function(s){return s.id;})};
  }""")


rep("  function launch(d){",
r"""  function launch(d){
    if(NATIVE){
      try{
        if(d.id==='_set') window.Aurora.openSettings();
        else if(d.kind==='source') window.Aurora.openInput(d.id,!!d.pass);
        else if(d.kind==='app') window.Aurora.launch(d.id);
      }catch(e){}
    }""")

rep("    if(k==='Escape'||k==='GoBack'||k==='Backspace'){\n      e.preventDefault();\n"
    "      if(veil.hasAttribute('data-on')) veil.removeAttribute('data-on');\n"
    "      else if(mode==='pop') closeEdit(false);\n"
    "      else if(mode==='lib') closeLib();\n      return;\n    }",
    "    if(k==='Escape'||k==='GoBack'||k==='Backspace'){ e.preventDefault(); goBack(); return; }")

rep("  window.aurora={show:show,count:PHOTOS.length,openLib:openLib,openEdit:function(){openEdit(focused);}};",
r"""  function goBack(){
    if(window.auroraWake) window.auroraWake();
    if(veil.hasAttribute('data-on')) veil.removeAttribute('data-on');
    else if(mode==='pop') closeEdit(false);
    else if(mode==='lib') closeLib();
  }
  window.auroraBack=goBack;
  window.auroraHome=function(){ veil.removeAttribute('data-on'); if(mode==='pop') closeEdit(false); if(mode==='lib') closeLib(); };
  window.auroraMenu=function(){ if((mode==='main'||mode==='lib')&&focused) openEdit(focused); };
  window.auroraNext=function(){ show(pickRandom()); schedule(); };
  window.auroraResume=function(){
    if(window.auroraWake) window.auroraWake();
    veil.removeAttribute('data-on');
    if(NATIVE){ loadNative(); favs=clean(favs); save(); if(mode==='main') renderMain(); else if(mode==='lib'){ var k=focused&&focused.dataset.key; buildLib(); setFocus((k&&lib.querySelector('[data-key="'+k+'"]'))||lib.querySelector('.app')); } }
  };
  if(NATIVE){ setTimeout(function(){ var h=document.querySelector('.hint'); if(h) h.className+=' gone'; },12000); }
  window.aurora={show:show,count:PHOTOS.length,openLib:openLib,openEdit:function(){openEdit(focused);}};""")

# native app icons: dark tile behind the real icon
rep("'<span class=\"ico\" style=\"--tint:'+(item.tint||'rgba(255,255,255,.16)')+'\">'",
    "'<span class=\"ico'+(item.native?' native':'')+'\" style=\"--tint:'+(item.tint||'rgba(255,255,255,.16)')+'\">'")

open(os.path.join(base, 'app', 'assets', 'index.html'), 'w').write(t + '\n</html>')
print('index.html', len(t))
