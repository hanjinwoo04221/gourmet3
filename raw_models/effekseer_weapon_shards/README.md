# Effekseer용 무기 이펙트 모델 (nail / fork / knife)

이 폴더의 모델들은 마인크래프트에 넣는 **강체(rigid) 아이템 모델이 아니라**, Effekseer의
`Model` 파츠(Arrow.efkmodel 샘플과 같은 종류)로 쓰라고 만든 **결정체/에너지 실루엣**입니다.
납작한 사각 블록이 아니라 각지고 테이퍼진 형태로 만들어서, Effekseer에서 가산 블렌드
(Additive) + 에미시브 컬러를 입히면 "칼/포크/못이 빛으로 응축된 것 같은" 이펙트가 됩니다.

## 파일 구성

| 파일 | 용도 |
|---|---|
| `nail_energy.obj` / `.gltf` | 네일 펀치・네일 건・13연타 |
| `fork_energy.obj` / `.gltf` | 포크・플라잉 포크 |
| `knife_energy.obj` / `.gltf` | 나이프・레그 나이프・플라잉 나이프 |
| `effekseer_weapon_shards.obj` / `.gltf` | 3개 전부 들어있는 통합본 (Blender에서 부분만 골라 쓰고 싶을 때) |
| `materials.mtl` | OBJ용 플레이스홀더 재질 (색은 Effekseer 에디터에서 다시 입힐 것이므로 흰색) |

각 파일은 원점(0,0,0) 기준, 무기가 +Y 방향(위쪽)으로 자라는 형태입니다. 길이는 대략
8~9 유닛(=0.5블록 상당의 스케일 단위이며, Effekseer는 자체 단위계를 쓰므로 가져온 뒤
`Model` 파츠의 Scale로 원하는 크기에 맞추면 됩니다.

## Effekseer로 가져오기

Effekseer Editor는 `.obj`/`.gltf`를 **직접 import**할 수 있습니다 (Blender를 꼭 거칠 필요
없음):

1. 새 노드를 만들고 타입을 **Model**로 변경합니다.
2. `Basic > Model` 항목에서 `nail_energy.gltf`(권장 — 재질/그룹 이름이 더 깔끔하게 들어옵니다)를
   선택해 import합니다. import되면 프로젝트 내부에 `.efkmodel`로 변환되어 저장됩니다.
3. `Renderer > Material`에서:
   - **Alpha Blend를 Add(가산)로 변경** — 결정체가 빛나는 느낌의 핵심입니다.
   - Lighting을 꺼서(Unlit) 월드 조명의 영향을 받지 않고 항상 밝게 빛나도록 합니다.
   - Color(RGBA)에 기술별 색을 입힙니다 — 모드 HUD와 통일감을 주려면:
     - 네일(못): `#FFD24B` (금색)
     - 포크: `#7FE3C8` (청록)
     - 나이프: `#C8F2FF` (하늘색)
4. `Color > Fade`나 `Scale` 타임라인으로 생성 시 확 커졌다가(0→120%) 유지 후 서서히
   사라지게 만들면 "짧게 번쩍이는 무기 형상" 느낌이 납니다.
5. 같은 이펙트 안에 이 Model 파츠 뒤에 얇은 Ring/Glow 스프라이트를 한 겹 더 겹치면
   (이미 번들된 `00_Basic/Simple_Ring_Shape1`, `02_Tktk03/Light` 등을 참고) 훨씬 화려해집니다.
6. 완성되면 `File > Export > Effect (.efkefc)`로 내보내
   `assets/gourmet2/effeks/toriko/`의 해당 슬롯에 저장하세요 — 정확한 파일명/위치는
   [`docs/EFFEKSEER_EXPORT.md`](../../docs/EFFEKSEER_EXPORT.md)의 매핑표를 따르면 됩니다
   (`nail_punch`, `nail_impact`, `fork`, `fork_impact`, `knife_slash`, `knife_impact` 등).

## Blender에서 다듬고 싶다면

`effekseer_weapon_shards.gltf`에는 `nail_energy` / `fork_energy` / `knife_energy` 이름의
그룹(빈 노드) 3개가 그대로 들어있으므로, Blender의 아웃라이너에서 원하는 그룹만 선택해
`File > Export > glTF 2.0`(Selected Objects 옵션 체크)으로 다시 내보내면 됩니다. OBJ는
그룹 노드가 없고 큐브별 이름만 있어 (`collar_a`, `prong_a_base` 등) 이름 패턴으로
직접 선택해야 합니다 — 그룹 구조가 필요하면 glTF 쪽을 쓰는 걸 권장합니다.

더 유기적인 결정체 모양으로 다듬고 싶다면 Blender에서 각 조각에 Bevel + Subdivision
Surface(Simple) 한 단계 정도만 얹어도 지금의 각진 실루엣이 훨씬 매끈한 크리스탈처럼
바뀝니다.
