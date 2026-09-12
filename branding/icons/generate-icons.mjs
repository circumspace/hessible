import fs from 'node:fs/promises';
import path from 'node:path';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const sharp = require(process.env.SHARP_MODULE || 'sharp');
const root = path.resolve(process.argv[2] || '.');
const write = async (p, data) => { p = path.join(root,p); await fs.mkdir(path.dirname(p),{recursive:true}); await fs.writeFile(p,data); };
// Interwoven ribbons form a single H without a separate letter or pictogram.
const warmBack = 'M300 324 C300 273 312 255 342 260 C414 269 452 307 453 373 L453 652 C453 720 411 764 347 764 C314 764 300 748 300 718 Z';
const coolBack = 'M724 324 C724 273 712 255 682 260 C610 269 572 307 571 373 L571 652 C571 720 613 764 677 764 C710 764 724 748 724 718 Z';
const rising = 'M682 260 C712 254 724 284 724 320 L724 420 C724 466 695 482 659 504 L421 642 C389 660 376 692 376 735 C376 755 330 778 309 752 C297 740 300 708 300 670 C300 626 330 601 371 577 L543 472 C570 455 577 430 577 391 L578 360 C582 312 615 275 682 260 Z';
const crossing = 'M342 260 C404 263 444 306 450 355 L453 392 C455 441 483 456 523 478 L651 543 C696 566 724 602 724 654 L724 718 C724 746 712 766 689 765 C665 749 660 724 637 700 C618 681 595 663 565 647 L360 535 C321 514 300 496 300 455 L300 320 C300 278 309 255 342 260 Z';
const ink = '#111B29';
const defs = '<defs><linearGradient id="field" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#1D2C42"/><stop offset="1" stop-color="#0C1421"/></linearGradient><linearGradient id="warmBack" x1="0" y1=".3" x2="1" y2=".9"><stop stop-color="#B92D28"/><stop offset=".52" stop-color="#D54B26"/><stop offset="1" stop-color="#FF993D"/></linearGradient><linearGradient id="coolBack" x1="0" y1=".2" x2="1" y2=".8"><stop stop-color="#24113D"/><stop offset=".5" stop-color="#32104F"/><stop offset="1" stop-color="#5B2794"/></linearGradient><linearGradient id="rising" x1="0" y1="1" x2="1" y2="0"><stop stop-color="#FFB248"/><stop offset=".23" stop-color="#FFE09A"/><stop offset=".49" stop-color="#49215F"/><stop offset=".76" stop-color="#7543A1"/><stop offset="1" stop-color="#B285CE"/></linearGradient><linearGradient id="crossing" x1="0" y1="0" x2="1" y2=".9"><stop stop-color="#FF6B65"/><stop offset=".26" stop-color="#FF9865"/><stop offset=".47" stop-color="#FFC273"/><stop offset=".76" stop-color="#7442A0"/><stop offset="1" stop-color="#46206F"/></linearGradient></defs>';
const glyph = color => '<g fill="'+color+'">'+[warmBack,coolBack,rising,crossing].map(d=>'<path d="'+d+'"/>').join('')+'</g>';
const mark = '<g><path d="'+warmBack+'" fill="url(#warmBack)"/><path d="'+coolBack+'" fill="url(#coolBack)"/><path d="'+rising+'" fill="url(#rising)"/><path d="'+crossing+'" fill="url(#crossing)"/><path d="M344 262 C404 265 443 307 449 355 L452 392 C454 441 482 457 522 479 L650 544" fill="none" stroke="#FFF1CF" stroke-opacity=".22" stroke-width="2"/></g>';
const svg = content => '<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">'+defs+content+'</svg>';
const tile = (x,y,w,r) => '<rect x="'+x+'" y="'+y+'" width="'+w+'" height="'+w+'" rx="'+r+'" fill="url(#field)"/>';
const desktop = svg(tile(64,64,896,202)+'<g transform="translate(512 512) scale(1.12) translate(-512 -512)">'+mark+'</g>');
const foreground = svg(`<g transform="translate(512 512) scale(.97) translate(-512 -512)">${mark}</g>`);
const background = svg(tile(0,0,1024,0));
const full = svg(`${tile(0,0,1024,0)}<g transform="translate(512 512) scale(1.12) translate(-512 -512)">${mark}</g>`);
const round = svg(`${tile(0,0,1024,512)}<g transform="translate(512 512) scale(1.12) translate(-512 -512)">${mark}</g>`);
const mono = svg(glyph('#FFFFFF'));
for(const [n,s] of Object.entries({'icon':desktop,'foreground':foreground,'background':background,'monochrome':mono,'store':full})) await write(`branding/icons/source/${n}.svg`,s+'\n');
async function png(s,n,p){ await write(p,await sharp(Buffer.from(s)).resize(n,n).png().toBuffer()); }
await png(desktop,1024,'icon.png');
await png(desktop,512,'app/src/main/res/drawable-nodpi/app_icon.png');
await png(full,512,'branding/icons/android/play-store-512.png');
// Android's 108dp layers retain the unscaled mark inside the circular safe zone.
for(const [density,legacy,adaptive] of [['mdpi',48,108],['hdpi',72,162],['xhdpi',96,216],['xxhdpi',144,324],['xxxhdpi',192,432]]){
 await png(desktop,legacy,`app/src/main/res/mipmap-${density}/ic_launcher.png`);
 await png(round,legacy,`app/src/main/res/mipmap-${density}/ic_launcher_round.png`);
 await png(foreground,adaptive,`app/src/main/res/mipmap-${density}/ic_launcher_foreground.png`);
}
const header = '<?xml version="1.0" encoding="utf-8"?>\n';
const adaptive = themed => header+`<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@drawable/ic_launcher_background" />\n    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n${themed?'    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n':''}</adaptive-icon>\n`;
for(const version of [26,33])for(const name of ['ic_launcher','ic_launcher_round'])await write(`app/src/main/res/mipmap-anydpi-v${version}/${name}.xml`,adaptive(version===33));
await write('app/src/main/res/drawable/ic_launcher_background.xml',header+'<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n    <gradient android:angle="315" android:startColor="#1D2C42" android:endColor="#0C1421" />\n</shape>\n');
// The native vector uses exactly the SVG geometry, with launcher-safe scaling.
const paths=[warmBack,coolBack,rising,crossing].map(d=>'        <path android:pathData="'+d+'" android:fillColor="#FFFFFF" />').join('\n');
await write('app/src/main/res/drawable/ic_launcher_monochrome.xml',header+'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="1024" android:viewportHeight="1024">\n    <group android:pivotX="512" android:pivotY="512" android:scaleX="0.97" android:scaleY="0.97">\n'+paths+'\n    </group>\n</vector>\n');
const macImages=[];
for(const size of [16,32,128,256,512])for(const scale of [1,2]){
 const name=`icon_${size}x${size}${scale===2?'@2x':''}.png`;
 await png(desktop,size*scale,`branding/icons/macos/Hessible.iconset/${name}`);
 macImages.push({idiom:'mac',size:`${size}x${size}`,scale:`${scale}x`,filename:name});
 await png(desktop,size*scale,`branding/icons/macos/AppIcon.appiconset/${name}`);
}
await write('branding/icons/macos/AppIcon.appiconset/Contents.json',JSON.stringify({images:macImages,info:{author:'xcode',version:1}},null,2)+'\n');
await png(foreground,1024,'branding/icons/macos/layers/foreground.png');
await png(background,1024,'branding/icons/macos/layers/background.png');
for(const n of [16,24,32,48,64,96,128,256,512,1024])await png(desktop,n,`branding/icons/linux/hicolor/${n}x${n}/apps/com.circumspace.contactstr.png`);
await write('branding/icons/linux/hicolor/scalable/apps/com.circumspace.contactstr.svg',desktop+'\n');
// Contact-sheet previews use the very same source geometry as exported assets.
const previewIcon = (s,x,y,n) => `<svg x="${x}" y="${y}" width="${n}" height="${n}" viewBox="0 0 1024 1024">${s.replace(/^<svg[^>]*>/,'').replace(/<\/svg>$/,'')}</svg>`;
const circular = svg(`${tile(0,0,1024,512)}<g transform="translate(512 512) scale(1.455) translate(-512 -512)">${mark}</g>`);
const android = svg(`${tile(0,0,1024,224)}<g transform="translate(512 512) scale(1.5) translate(-512 -512)">${mark}</g>`);
const themed = svg(glyph('#DACEFA'));
const monoTile = svg(`<rect width="1024" height="1024" rx="512" fill="#34264E"/><g transform="translate(512 512) scale(1.455) translate(-512 -512)">${themed.replace(/^<svg[^>]*>/,'').replace(/<\/svg>$/,'')}</g>`);
const board = `<svg xmlns="http://www.w3.org/2000/svg" width="1440" height="1000" viewBox="0 0 1440 1000"><rect width="1440" height="1000" fill="#F5F2F9"/><g font-family="Helvetica,Arial,sans-serif" fill="#241839"><text x="76" y="90" font-size="18" letter-spacing="4">HESSIBLE</text><text x="76" y="150" font-size="40" font-weight="700">Hessible. A different kind of connection.</text><text x="76" y="193" font-size="20" fill="#776B86">Interwoven color. An unmistakable H.</text>${previewIcon(desktop,82,232,510)}<text x="720" y="275" font-size="16" letter-spacing="2">ONE MARK, EVERY PLATFORM</text>${previewIcon(circular,724,310,150)}${previewIcon(desktop,927,299,174)}${previewIcon(monoTile,1150,310,150)}<text x="750" y="496" font-size="17">Android</text><text x="951" y="496" font-size="17">Desktop</text><text x="1161" y="496" font-size="17">Themed</text><path d="M720 535H1320" stroke="#DFD8E8"/><text x="720" y="582" font-size="16" letter-spacing="2">SMALL SIZES</text>${[16,24,32,48,64].map((n,i)=>previewIcon(desktop,726+i*116,625+64-n,n)).join('')}${[16,24,32,48,64].map((n,i)=>`<text x="${726+i*116}" y="724" font-size="15" fill="#776B86">${n}px</text>`).join('')}<rect x="76" y="805" width="1288" height="125" rx="24" fill="#261B38"/>${previewIcon(desktop,100,817,100)}<text x="230" y="854" fill="#F2ECFF" font-size="21" font-weight="700">Hessible</text><text x="230" y="888" fill="#BEB1CF" font-size="17">Editable vector master · Adaptive Android layers · macOS &amp; Linux exports</text></g></svg>`;
await write('branding/icons/preview.svg',board);
await write('branding/icons/preview.png',await sharp(Buffer.from(board)).png().toBuffer());
// PNG-backed ICNS representations, including standard and Retina slots.
const chunks=[];
for(const [type,n] of [['icp4',16],['icp5',32],['icp6',64],['ic07',128],['ic08',256],['ic09',512],['ic10',1024],['ic11',32],['ic12',64],['ic13',256],['ic14',512]]){
 const data=await sharp(Buffer.from(desktop)).resize(n,n).png().toBuffer();
 const header=Buffer.alloc(8); header.write(type); header.writeUInt32BE(data.length+8,4); chunks.push(header,data);
}
const icnsHeader=Buffer.alloc(8); icnsHeader.write('icns'); icnsHeader.writeUInt32BE(chunks.reduce((n,b)=>n+b.length,8),4);
await write('branding/icons/macos/Hessible.icns',Buffer.concat([icnsHeader,...chunks]));
console.log(`Generated icon assets in ${root}`);
