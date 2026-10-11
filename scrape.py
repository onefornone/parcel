import json,re,urllib.request,urllib.parse,html,os
from concurrent.futures import ThreadPoolExecutor
UA={"User-Agent":"Mozilla/5.0"}
def get(u,t=25): return urllib.request.urlopen(urllib.request.Request(u,headers=UA),timeout=t)
def exists(u):
    try: urllib.request.urlopen(urllib.request.Request(u,headers=UA,method="HEAD"),timeout=15); return True
    except Exception: return False
# (repo, name, platform, category, description, android package)
A=[
("TeamNewPipe/NewPipe","NewPipe","phone","Media","Lightweight YouTube client with background play.","org.schabi.newpipe"),
("libre-tube/LibreTube","LibreTube","phone","Media","Privacy-friendly YouTube frontend.","com.github.libretube"),
("JunkFood02/Seal","Seal","phone","Media","Video and audio downloader built on yt-dlp.","com.junkfood.seal"),
("jellyfin/jellyfin-android","Jellyfin","phone","Media","Stream your own media library.","org.jellyfin.mobile"),
("mihonapp/mihon","Mihon","phone","Reading","Free, open-source manga reader.","app.mihon"),
("ankidroid/Anki-Android","AnkiDroid","phone","Reading","Flashcards that fit how memory works.","com.ichi2.anki"),
("laurent22/joplin","Joplin","phone","Reading","Notes and to-dos with sync and encryption.","net.cozic.joplin"),
("termux/termux-app","Termux","phone","Tools","Terminal emulator and Linux environment.","com.termux"),
("ImranR98/Obtainium","Obtainium","phone","Tools","Get app updates straight from their source pages.","dev.imranr.obtainium"),
("FossifyOrg/Gallery","Fossify Gallery","phone","Tools","Offline photo and video gallery.","org.fossify.gallery"),
("zhanghai/MaterialFiles","Material Files","phone","Tools","Clean, material-design file manager.","me.zhanghai.android.files"),
("RikkaApps/Shizuku","Shizuku","phone","Tools","Run privileged operations from other apps.","moe.shizuku.privileged.api"),
("LawnchairLauncher/lawnchair","Lawnchair","phone","Tools","Customizable Pixel-style launcher.","app.lawnchair"),
("NeoApplications/Neo-Store","Neo Store","phone","Tools","Material-styled F-Droid client.","com.machiav3lli.fdroid"),
("Droid-ify/client","Droid-ify","phone","Tools","Simple F-Droid client.","com.looker.droidify"),
("localsend/localsend","LocalSend","phone","Tools","Share files to nearby devices, no internet needed.","org.localsend.localsend_app"),
("beemdevelopment/Aegis","Aegis Authenticator","phone","Security","Secure 2FA app with encrypted backups.","com.beemdevelopment.aegis"),
("Kunzisoft/KeePassDX","KeePassDX","phone","Security","KeePass-compatible password manager.","com.kunzisoft.keepass.libre"),
("organicmaps/organicmaps","Organic Maps","phone","Travel","Offline maps and navigation on OpenStreetMap.","app.organicmaps"),
("streetcomplete/StreetComplete","StreetComplete","phone","Travel","Improve OpenStreetMap with simple quests.","de.westnordost.streetcomplete"),
("M66B/FairEmail","FairEmail","phone","Social","Privacy-oriented email client.","eu.faircode.email"),
("tuskyapp/Tusky","Tusky","phone","Social","Lightweight Mastodon client.","com.keylesspalace.tusky"),
("RookieEnough/Orion-Store","Orion Store","phone","Tools","The store that inspired Parcel.",None),
("yuliskov/SmartTube","SmartTube","tv","Video","Ad-free YouTube for Android TV.","com.teamsmart.videomanager"),
("jellyfin/jellyfin-androidtv","Jellyfin for TV","tv","Video","Your Jellyfin library on the big screen.","org.jellyfin.androidtv"),
("mpv-android/mpv-android","mpv","tv","Video","Powerful video player for any format.","is.xyz.mpv"),
("moonlight-stream/moonlight-android","Moonlight","tv","Games","Stream PC games to your TV.","com.limelight"),
("truefedex/tv-bro","TV Bro","tv","Browser","Web browser built for TV remotes.","com.phlox.tvwebbrowser"),
("DonutWare/Fladder","Fladder","tv","Video","Modern Jellyfin client for TV.","nl.jknaapen.fladder"),
("obsidianmd/obsidian-releases","Obsidian","pc","Notes","Private notes in plain Markdown files.",None),
("laurent22/joplin","Joplin","pc","Notes","Notes and to-dos with sync and encryption.",None),
("VSCodium/vscodium","VSCodium","pc","Dev","VS Code without Microsoft telemetry.",None),
("notepad-plus-plus/notepad-plus-plus","Notepad++","pc","Dev","Fast, tabbed text and code editor.",None),
("ip7z/7zip","7-Zip","pc","Utilities","Free file archiver with strong compression.",None),
("pbatard/rufus","Rufus","pc","Utilities","Create bootable USB drives.",None),
("ventoy/Ventoy","Ventoy","pc","Utilities","One USB stick, many bootable ISOs.",None),
("localsend/localsend","LocalSend","pc","Utilities","Share files to nearby devices, no internet needed.",None),
("syncthing/syncthing","Syncthing","pc","Utilities","Continuous private file sync between devices.",None),
("yt-dlp/yt-dlp","yt-dlp","pc","Media","Download video and audio from thousands of sites.",None),
("obsproject/obs-studio","OBS Studio","pc","Media","Record and live-stream your screen.",None),
("ShareX/ShareX","ShareX","pc","Media","Screenshots, screen recording and sharing.",None)]
REL={};IMG={}
def rel(r):
    if r in REL: return REL[r]
    tag=get(f"https://github.com/{r}/releases/latest").geturl().split("/tag/")[1]
    h=get(f"https://github.com/{r}/releases/expanded_assets/{tag}").read().decode();fs=[];M={"KB":1e3,"MB":1e6,"GB":1e9}
    for m in re.finditer(r'<li[^>]*>(.*?)</li>',h,re.S):
        b=m.group(1);l=re.search(r'href="(/[^"]+/releases/download/[^"]+)"',b)
        if not l: continue
        s=re.search(r'([\d.]+)\s*(KB|MB|GB)',b)
        fs.append({"n":html.unescape(l.group(1).split("/")[-1]),"s":int(float(s.group(1))*M[s.group(2)]) if s else 0,"u":"https://github.com"+html.unescape(l.group(1))})
    dt=re.search(r'datetime="(\d{4}-\d\d-\d\d)',h)
    REL[r]=(urllib.parse.unquote(tag),dt.group(1) if dt else "",fs);return REL[r]
def osof(n):
    l=n.lower()
    if re.search(r'source|src|sha\d|checksum|\.sig$|\.asc$|sbom|symbols|debug|blockmap|\.ya?ml$|\.txt$|\.json$|\.sum$|\.apk$|\.iso$|\.zsync$|\.sh$|\.md$|\.pdb$',l): return None
    if l.endswith((".exe",".msi",".msix")) or re.search(r'win',l): return "windows"
    if l.endswith((".dmg",".pkg")) or re.search(r'mac|darwin|osx',l): return "mac"
    if l.endswith((".appimage",".deb",".rpm",".flatpak",".snap")) or "linux" in l: return "linux"
def pcfiles(fs):
    out=[];cnt={}
    for f in fs:
        o=osof(f["n"])
        if o and cnt.get(o,0)<6: cnt[o]=cnt.get(o,0)+1;out.append({**f,"os":o})
    return out
def media(r):
    if r in IMG: return IMG[r]
    B=f"https://raw.githubusercontent.com/{r}/HEAD/"
    for base,sd,ic in [("fastlane/metadata/android/en-US/images/","phoneScreenshots/","icon.png"),("metadata/en-US/images/","phoneScreenshots/","icon.png"),("app/src/main/play/listings/en-US/graphics/","phone-screenshots/","icon/icon.png")]:
        shots=[]
        for i in range(0,9):
            hit=next((n for n in (f"{i}.png",f"{i}.jpg",f"0{i}.png",f"0{i}.jpg") if exists(B+base+sd+n)),None)
            if hit: shots.append(B+base+sd+hit)
            elif shots or i>=2: break
        icon=B+base+ic if exists(B+base+ic) else None
        if shots or icon: IMG[r]=(icon,shots[:6]);return IMG[r]
    IMG[r]=(None,[]);return IMG[r]
OLD={}
if os.path.exists("www/apps.json"):
    for a in json.load(open("www/apps.json")): OLD[a["id"]]=a
def build(row):
    r,n,pl,c,d,p=row;aid=r if pl=="phone" else f"{r}#{pl}"
    try:
        tag,dt,fs=rel(r)
        fs=pcfiles(fs) if pl=="pc" else [f for f in fs if f["n"].lower().endswith(".apk")]
        if not fs: raise Exception("no files")
        ic,sh=media(r)
        return {"id":aid,"repo":r,"name":n,"plat":pl,"cat":c,"desc":d,"pkg":p,"tag":tag,"date":dt,"icon":ic,"shots":sh,"files":fs}
    except Exception as e:
        print("keep/skip",aid,e);return OLD.get(aid)
for r,*_ in A: pass
with ThreadPoolExecutor(12) as ex: out=[x for x in ex.map(build,A) if x]
for pl in ("phone","tv","pc"): print(pl,len([a for a in out if a["plat"]==pl]),"apps;",len([a for a in out if a["plat"]==pl and a["shots"]]),"with screenshots;",len([a for a in out if a["plat"]==pl and a["icon"]]),"with icon")
if out: json.dump(out,open("www/apps.json","w"),separators=(",",":"))
