AstraxSkill - Paper 1.21.x (Java 21)

BUILD:   mvn clean package   ->  target/AstraxSkill.jar  (bo vao thu muc plugins/)

LENH (can quyen astrax.admin):
  /astrax bind <skill> [cap]    ep skill vao mon dang cam
  /astrax unbind <skill|all>
  /astrax set <id|none>         gan mon dang cam vao set
  /astrax give <nguoi> <setid>  phat tron bo set (cau hinh trong config.yml)
  /astrax list | reload

CACH DUNG:
  Kiem: danh co ti le kich hoat | Chuot phai = skill chu dong
  Giap: bi danh co ti le kich hoat | Shift + chuot phai = skill chu dong
  Set: mac/cam du 2 / 4 / 5 mon cung set -> nhan bonus (sua trong config.yml)

BAN CHO MEMBER: dung plugin shop/crate/Vault goi lenh
  astrax give %player% astrax   (console command khi member mua)

===== KIEM HAC LONG (ASTRAX) =====
  /astrax hac give <nguoi> [cap 1-3]   tang Kiem Hac Long
  /astrax hac apply [cap]              ep Hac Long vao thanh kiem dang cam (kiem rieng cua ban)
  /astrax hac tier <1-3>               chinh cap kiem dang cam
  /astrax hac stone <nguoi> [sl]       tang Da Nang Cap (keo tha len kiem trong tui do)

  Chuot phai = Long Trang | Phim F = Hac Long Giang The
  Noi tai Long Huyet 12% | Danh du 100 nang luong -> Long No 10s
  Resource pack: dien CustomModelData vao hac-long.model-data trong config.yml
  Shop: lenh console  astrax hac give %player% 1   /   astrax hac stone %player% 1

===== CYBER DRAGON (giap noi tai, chi can mac) =====
  /astrax cyber give <nguoi>                tang tron bo 4 mon
  /astrax cyber piece <nguoi> <slot>        tang 1 mon (helmet/chestplate/leggings/boots)
  /astrax cyber apply                       ep noi tai vao mon giap dang cam
  Mu +10% dmg | Giap nguc -12% dmg nhan | Quan +15% toc do | Giay +20% toc do
  Du 4 mon: Cyber Shield, Overdrive, Energy Absorption, Ultimate Mode (tu dong)
  Shop: lenh console  astrax cyber give %player%

GLOWING: mac bat ky 1 mon giap VIP (Cyber Dragon / set Astrax) -> phat sang. Chinh trong config.yml muc glowing.
