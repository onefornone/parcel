import json,re,urllib.request,urllib.parse,html
A=[("TeamNewPipe/NewPipe","NewPipe","Media","Lightweight YouTube client with background play.","org.schabi.newpipe"),
("ImranR98/Obtainium","Obtainium","Tools","Get app updates straight from their source pages.","dev.imranr.obtainium"),
("termux/termux-app","Termux","Tools","Terminal emulator and Linux environment.","com.termux"),
("beemdevelopment/Aegis","Aegis Authenticator","Security","Secure 2FA app with encrypted backups.","com.beemdevelopment.aegis"),
("organicmaps/organicmaps","Organic Maps","Travel","Offline maps and navigation on OpenStreetMap.","app.organicmaps"),
("libre-tube/LibreTube","LibreTube","Media","Privacy-friendly YouTube frontend.","com.github.libretube"),
("mihonapp/mihon","Mihon","Reading","Free, open-source manga reader.","app.mihon"),
("JunkFood02/Seal","Seal","Media","Video and audio downloader built on yt-dlp.","com.junkfood.seal"),
("FossifyOrg/Gallery","Fossify Gallery","Tools","Offline photo and video gallery.","org.fossify.gallery"),
("zhanghai/MaterialFiles","Material Files","Tools","Clean, material-design file manager.","me.zhanghai.android.files"),
("RikkaApps/Shizuku","Shizuku","Tools","Run privileged operations from other apps.","moe.shizuku.privileged.api"),
("Kunzisoft/KeePassDX","KeePassDX","Security","KeePass-compatible password manager.","com.kunzisoft.keepass.libre"),
("streetcomplete/StreetComplete","StreetComplete","Travel","Improve OpenStreetMap with simple quests.","de.westnordost.streetcomplete"),
("M66B/FairEmail","FairEmail","Social","Privacy-oriented email client.","eu.faircode.email"),
("tuskyapp/Tusky","Tusky","Social","Lightweight Mastodon client.","com.keylesspalace.tusky"),
("ankidroid/Anki-Android","AnkiDroid","Reading","Flashcards that fit how memory works.","com.ichi2.anki"),
("LawnchairLauncher/lawnchair","Lawnchair","Tools","Customizable Pixel-style launcher.","app.lawnchair"),
("NeoApplications/Neo-Store","Neo Store","Tools","Material-styled F-Droid client.","com.machiav3lli.fdroid"),
("Droid-ify/client","Droid-ify","Tools","Simple F-Droid client.","com.looker.droidify"),
("RookieEnough/Orion-Store","Orion Store","Tools","The store that inspired Parcel.",None)]
def get(u): return urllib.request.urlopen(urllib.request.Request(u,headers={"User-Agent":"Mozilla/5.0"}),timeout=25)
import os
OLD={a['id']:a for a in json.load(open('www/apps.json'))} if os.path.exists('www/apps.json') else {}
out=[];M={"KB":1e3,"MB":1e6,"GB":1e9}
for r,n,c,d,p in A:
    try:
        tag=get(f"https://github.com/{r}/releases/latest").geturl().split("/tag/")[1]
        h=get(f"https://github.com/{r}/releases/expanded_assets/{tag}").read().decode();ap=[]
        for m in re.finditer(r'<li[^>]*>(.*?)</li>',h,re.S):
            b=m.group(1);l=re.search(r'href="(/[^"]+/releases/download/[^"]+\.apk)"',b)
            if not l: continue
            s=re.search(r'([\d.]+)\s*(KB|MB|GB)',b)
            ap.append({"n":html.unescape(l.group(1).split("/")[-1]),"s":int(float(s.group(1))*M[s.group(2)]) if s else 0,"u":"https://github.com"+html.unescape(l.group(1))})
        dt=re.search(r'datetime="(\d{4}-\d\d-\d\d)',h)
        if ap: out.append({"id":r,"name":n,"cat":c,"desc":d,"pkg":p,"tag":urllib.parse.unquote(tag),"date":dt.group(1) if dt else "","apks":ap});print("ok",n,tag,len(ap))
        else: print("noapk",n);out.extend([OLD[r]] if r in OLD else [])
    except Exception as e: print("fail",n,e);out.extend([OLD[r]] if r in OLD else [])
if out: json.dump(out,open("www/apps.json","w"),separators=(",",":"))
print(len(out))
