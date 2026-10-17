#!/data/data/com.termux/files/usr/bin/bash
# ApexStudio ANIMATED sticker pack generator (Bug 2).
# Original material, no CapCut/TikTok artwork. Lottie Simple License.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PKG="$ROOT/app/src/main/assets/stickers/animated"
FR=30; OP=60; DARK='0.07, 0.07, 0.1, 1'
mkdir -p "$PKG"/{hearts,stars,fire,confetti,emojis,animals}
fill()  { printf '{"ty":"fl","nm":"F","c":{"a":0,"k":[%s]},"o":{"a":0,"k":100}}' "$1"; }
xf()    { printf '{"ty":"tr","nm":"T","p":{"a":0,"k":[%s]},"a":{"a":0,"k":[0,0]},"s":{"a":0,"k":[100,100]},"r":{"a":0,"k":%s},"o":{"a":0,"k":100}}' "$1" "${2:-0}"; }
jn()    { local IFS=,; printf '%s' "$*"; }
grp()   { local n="$1"; shift; printf '{"ty":"gr","nm":"%s","it":[%s]}' "$n" "$(jn "$@")"; }
el()    { printf '{"ty":"el","nm":"E","p":{"a":0,"k":[%s]},"s":{"a":0,"k":[%s,%s]},"d":1}' "$1" "$2" "$2"; }
ell()   { printf '{"ty":"el","nm":"E","p":{"a":0,"k":[%s]},"s":{"a":0,"k":[%s,%s]},"d":1}' "$1" "$2" "$3"; }
rcs()   { printf '{"ty":"rc","nm":"R","p":{"a":0,"k":[%s]},"s":{"a":0,"k":[%s,%s]},"r":{"a":0,"k":%s}}' "$1" "$2" "$3" "${4:-0}"; }
srs()   { printf '{"ty":"sr","nm":"S","p":{"a":0,"k":[%s]},"pt":{"a":0,"k":%s},"or":{"a":0,"k":%s},"ir":{"a":0,"k":%s},"is":{"a":0,"k":0},"os":{"a":0,"k":0},"r":%s,"d":1}' "$1" "$2" "$3" "$4" "${5:-0}"; }
shape() {
  local k="$1" c="$2"
  case "$k" in
    circle)   grp "C" "$(el '0,0' 88)" "$(fill "$c")" "$(xf '0,0')" ;;
    rect)     grp "R" "$(rcs '0,0' 62 62 12)" "$(fill "$c")" "$(xf '0,0')" ;;
    heart)    grp "H" "$(el '-24,-16' 56)" "$(fill "$c")" \
                      "$(el '24,-16' 56)" "$(fill "$c")" \
                      "$(grp "D" "$(rcs '0,0' 64 64 10)" "$(fill "$c")" "$(xf '0,18' 45)")" \
                      "$(xf '0,0')" ;;
    spark)    grp "P" "$(srs '0,0' 4 58 16)" "$(fill "$c")" "$(xf '0,0')" ;;
    star)     grp "S" "$(srs '0,0' 5 56 24)" "$(fill "$c")" "$(xf '0,0')" ;;
    triangle) grp "T" "$(srs '0,0' 3 62 34)" "$(fill "$c")" "$(xf '0,0')" ;;
    flame)    grp "F" "$(el '0,6' 70)" "$(fill "$c")" \
                      "$(el '0,22' 38)" "$(fill '1,0.95,0.55,1')" "$(xf '0,0')" ;;
    face)     grp "A" "$(el '0,0' 96)" "$(fill "$c")" \
                      "$(ell '-21,-14' 15 15)" "$(fill "$DARK")" \
                      "$(ell '21,-14' 15 15)" "$(fill "$DARK")" \
                      "$(rcs '0,22' 44 14 7)" "$(fill "$DARK")" "$(xf '0,0')" ;;
    cat)      grp "K" "$(srs '-32,-44' 3 30 6 -15)" "$(fill "$c")" \
                      "$(srs '32,-44' 3 30 6 15)" "$(fill "$c")" \
                      "$(el '0,0' 86)" "$(fill "$c")" \
                      "$(ell '-19,-8' 16 16)" "$(fill "$DARK")" \
                      "$(ell '19,-8' 16 16)" "$(fill "$DARK")" \
                      "$(el '0,16' 18)" "$(fill '1,0.55,0.6,1')" "$(xf '0,0')" ;;
    bunny)    grp "B" "$(ell '-26,-60' 26 78)" "$(fill "$c")" \
                      "$(ell '26,-60' 26 78)" "$(fill "$c")" \
                      "$(el '0,0' 84)" "$(fill "$c")" \
                      "$(ell '-19,-8' 14 14)" "$(fill "$DARK")" \
                      "$(ell '19,-8' 14 14)" "$(fill "$DARK")" \
                      "$(rcs '0,20' 30 12 6)" "$(fill "$DARK")" "$(xf '0,0')" ;;
    frog)     grp "R" "$(ell '-30,-44' 36 36)" "$(fill "$c")" "$(ell '-30,-44' 19 19)" "$(fill "$DARK")" \
                      "$(ell '30,-44' 36 36)" "$(fill "$c")" "$(ell '30,-44' 19 19)" "$(fill "$DARK")" \
                      "$(el '0,6' 84)" "$(fill "$c")" \
                      "$(rcs '0,26' 52 16 8)" "$(fill "$DARK")" "$(xf '0,0')" ;;
    confetti) printf '%s,%s,%s' \
                      "$(grp "P1" "$(rcs '-48,-46' 34 26 8)" "$(fill "$c")" "$(xf '0,0' -18)")" \
                      "$(grp "P2" "$(rcs '46,-20' 32 24 8)" "$(fill '0.2,0.8,0.45,1')" "$(xf '0,0' 24)")" \
                      "$(grp "P3" "$(rcs '-6,52' 40 22 8)"  "$(fill '0.2,0.55,1,1')"   "$(xf '0,0' 10)")" ;;
    *) echo "unknown shape: $k" >&2; exit 2 ;;
  esac
}
ks_for() {
  case "$1" in
    pulse) printf '{"o":{"a":0,"k":100},"r":{"a":0,"k":0},"p":{"a":0,"k":[100,100,0]},"a":{"a":0,"k":[0,0,0]},"s":{"a":1,"k":[{"t":0,"s":[100,100,100]},{"t":15,"s":[132,132,100]},{"t":30,"s":[100,100,100]},{"t":45,"s":[120,120,100]},{"t":60,"s":[100,100,100]}]}}' ;;
    spin) printf '{"o":{"a":0,"k":100},"r":{"a":1,"k":[{"t":0,"s":[0]},{"t":60,"s":[360]}]},"p":{"a":0,"k":[100,100,0]},"a":{"a":0,"k":[0,0,0]},"s":{"a":0,"k":[100,100,100]}}' ;;
    rise) printf '{"o":{"a":0,"k":100},"r":{"a":0,"k":0},"p":{"a":1,"k":[{"t":0,"s":[100,64,0]},{"t":30,"s":[100,136,0]},{"t":60,"s":[100,64,0]}]},"a":{"a":0,"k":[0,0,0]},"s":{"a":0,"k":[100,100,100]}}' ;;
    flicker) printf '{"o":{"a":1,"k":[{"t":0,"s":[100]},{"t":15,"s":[60]},{"t":30,"s":[100]},{"t":45,"s":[65]},{"t":60,"s":[100]}]},"r":{"a":0,"k":0},"p":{"a":0,"k":[100,100,0]},"a":{"a":0,"k":[0,0,0]},"s":{"a":1,"k":[{"t":0,"s":[100,100,100]},{"t":15,"s":[92,110,100]},{"t":30,"s":[100,100,100]},{"t":45,"s":[96,106,100]},{"t":60,"s":[100,100,100]}]}}' ;;
    pop) printf '{"o":{"a":0,"k":100},"r":{"a":0,"k":0},"p":{"a":0,"k":[100,100,0]},"a":{"a":0,"k":[0,0,0]},"s":{"a":1,"k":[{"t":0,"s":[0,0,100]},{"t":12,"s":[126,126,100]},{"t":20,"s":[100,100,100]},{"t":60,"s":[100,100,100]}]}}' ;;
    wobble) printf '{"o":{"a":0,"k":100},"r":{"a":1,"k":[{"t":0,"s":[-14]},{"t":15,"s":[14]},{"t":30,"s":[-14]},{"t":45,"s":[14]},{"t":60,"s":[-14]}]},"p":{"a":0,"k":[100,100,0]},"a":{"a":0,"k":[0,0,0]},"s":{"a":0,"k":[100,100,100]}}' ;;
    *) echo "unknown style: $1" >&2; exit 2 ;;
  esac
}
ENTRIES='[]'
emit() {
  local cat="$1" file="$2" nm="$3" kind="$4" col="$5" st="$6" tg="$7"
  local ks shp lyr
  ks="$(ks_for "$st")"
  shp="$(shape "$kind" "$col")"
  lyr="$(printf '{"ddd":0,"ind":1,"ty":4,"nm":"%s","sr":1,"ks":%s,"ao":0,"shapes":[%s],"ip":0,"op":%s,"st":0}' "$file" "$ks" "$shp" "$OP")"
  printf '{"v":"5.7.4","fr":%s,"ip":0,"op":%s,"w":200,"h":200,"nm":"%s","ddd":0,"assets":[],"layers":[%s],"ip":0,"op":%s,"st":0}' \
    "$FR" "$OP" "$nm" "$lyr" "$OP" > "$PKG/$cat/$file.json"
  ENTRIES="$(jq -c --arg id "$cat/$file" --arg f "$cat/$file.json" --arg n "$nm" --arg c "$cat" --arg t "$tg" \
    '.+[{id:$id,file:$f,name:$n,category:$c,tags:($t|split(","))}]' <<<"$ENTRIES")"
}
emit hearts beating-heart  "Heartbeat"      heart    '1,0.18,0.35,1'  pulse  "love,heart,beat,idol,red"
emit hearts floating-heart "Floating Heart" heart    '1,0.45,0.62,1'  rise   "love,heart,float,cute,pink"
emit hearts heart-burst    "Heart Burst"    heart    '1,0.3,0.4,1'    pop    "love,heart,pop,excited,party"
emit hearts wobbly-heart   "Wobbly Heart"   heart    '0.6,0.4,1,1'    wobble "love,heart,purple,silly,cute"
emit stars spinning-star   "Star Spin"      star     '1,0.77,0.25,1'  spin   "star,spin,gold,shiny,yellow"
emit stars twinkle-star    "Twinkle"        star     '1,0.84,0.16,1'  flicker "star,twinkle,shine,night,yellow"
emit stars sparkle         "Sparkle"        spark    '1,1,1,1'        pop    "sparkle,shine,magic,white,glitter"
emit stars wobble-star     "Wobbly Star"    star     '1,0.6,0.2,1'    wobble "star,orange,silly,cute,wobble"
emit fire  flame           "Flame"          flame    '1,0.55,0.12,1'  flicker "fire,flame,lit,hot,orange"
emit fire  rising-flame    "Rising Flame"   flame    '1,0.35,0.08,1'  rise   "fire,flame,rise,hot,ember"
emit fire  fireball        "Fireball"       circle   '1,0.42,0.1,1'   pulse  "fire,ball,lava,hot,round"
emit fire  campfire        "Campfire"       flame    '1,0.65,0.2,1'   pulse  "fire,campfire,warm,outdoor,bonfire"
emit confetti confetti-fall "Confetti Fall" confetti '1,0.84,0.16,1' rise   "confetti,fall,party,celebration,color"
emit confetti confetti-pop  "Confetti Pop"  confetti '1,0.77,0.25,1' pop    "confetti,pop,explosion,party,burst"
emit confetti party-arrow   "Party Triangle" triangle '0.6,0.4,1,1'  spin   "party,triangle,flag,celebration,purple"
emit confetti ribbon        "Ribbon"        rect     '0.2,0.8,0.45,1' wobble "ribbon,confetti,green,streamer,party"
emit emojis  smiley         "Smiley"        face     '1,0.84,0.16,1'  pulse  "emoji,smile,happy,face,joy"
emit emojis  wink           "Wink"          face     '1,0.77,0.25,1'  wobble "emoji,wink,fun,face,playful"
emit emojis  blush-face     "Blush"         face     '1,0.55,0.4,1'   rise   "emoji,blush,cute,face,shy"
emit animals kitty          "Kitty"         cat      '1,0.9,0.85,1'   wobble "animal,cat,kitty,pet,face"
emit animals bunny          "Bunny"         bunny    '1,0.8,0.85,1'   pulse  "animal,bunny,rabbit,pet,cute"
emit animals froggy         "Froggy"        frog     '0.3,0.78,0.4,1' rise   "animal,frog,green,pond,face"
jq -n --argjson a "$ENTRIES" '{version:1,pack:"ApexStudio Animated (original)",license:"Lottie Simple License",source:"https://lottiefiles.com/lottie-simple-license",attribution:"Original animation code written for ApexStudio. No third-party CapCut/TikTok/LottieFiles artwork was copied.",count:($a|length),animations:$a}' > "$PKG/manifest.json"
echo "generated=$(find "$PKG" -name '*.json' ! -name manifest.json | wc -l)"
