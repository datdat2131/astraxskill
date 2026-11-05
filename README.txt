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
\nFLAT GITHUB BUILD:\n  Java files, plugin.yml, config.yml and pom.xml are at repository root.\n  GitHub Actions workflow: .github/workflows/main.yml\n  Java 21 + Paper API 1.21.1.\n