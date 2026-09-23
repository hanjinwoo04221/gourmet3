# Effekseer용 무기 이펙트 모델 — 매끄러운(Blender/Subsurf) 버전

`raw_models/effekseer_weapon_shards/`(Blockbench, 각진 저폴리)의 후속작입니다. 이번엔
**실제로 Blender를 원격 구동**해서 만들었습니다 — 8각형 콘/아이코스피어 원시 도형을 조합한 뒤
**Subdivision Surface(레벨 2)를 적용**해 모서리를 둥글려, 각지지 않고 유기적으로 흐르는
결정체/에너지 형상이 됐습니다. 못은 매끈한 방추형 스파이크, 포크는 구체 코어에서 뻗어나가는
4개의 둥근 창날, 나이프는 실제로 납작한(두께가 폭의 약 68%) 양날 블레이드 형태입니다.

용도와 Effekseer 연동 방법(Add 블렌드, Unlit, 기술별 색상 등)은 이전 폴더의
[`../effekseer_weapon_shards/README.md`](../effekseer_weapon_shards/README.md)와 동일하니
그대로 참고하시면 됩니다 — 여기서는 **어떤 파일을 쓸지**만 안내합니다.

## 파일 구성

| 파일 | 용도 |
|---|---|
| `nail_energy_smooth.obj` / `.glb` | 네일 펀치・네일 건・13연타 |
| `fork_energy_smooth.obj` / `.glb` | 포크・플라잉 포크 |
| `knife_energy_smooth.obj` / `.glb` | 나이프・레그 나이프・플라잉 나이프 |
| `effekseer_weapon_shards_smooth.obj` / `.glb` | 3개 전부 들어있는 통합본 |

지난 버전과 달리 `.gltf`(JSON+base64) 대신 **`.glb`(단일 바이너리)**로 내보냈습니다 — 이번
Blender 5.2 내장 glTF 익스포터가 그 형식만 지원해서인데, 오히려 파일 하나로 끝나서 Effekseer에
가져오기엔 더 편합니다. `.obj`는 이전과 동일하게 `.mtl`이 자동으로 같이 생성됐습니다.

## 검증

렌더로 직접 확인한 결과입니다 (스크린샷은 `raw_models/`에 남기지 않았습니다 — 필요하시면
말씀해주세요):
- 못: 원형 단면, 폭·깊이 2.50 / 높이 7.87 — 매끈한 원뿔형 스파이크
- 포크: 폭 3.54 / 깊이 2.63 / 높이 8.64 — 4개 창날이 양옆으로 부채꼴로 펼쳐짐
- 나이프: 폭 2.31 / **깊이 1.57**(폭의 68%) / 높이 10.13 — 납작한 블레이드 단면 확인됨

## 참고 — 어떻게 만들었는지

Blender MCP를 Claude Code 도구 목록에 붙이는 데는 실패했습니다(공식 Blender Lab MCP
익스텐션과 기존에 설정돼 있던 커뮤니티 브릿지의 프로토콜이 서로 달라 응답이 멈추는 문제).
대신 애드온이 실제로 여는 로컬 소켓(9876번 포트, null-byte로 구분된 JSON RPC)에 직접 접속해
Blender의 Python API(`bpy`)를 원격 실행하는 방식으로 처리했습니다. 지금 열려 있는 Blender
5.2 세션에 생성된 오브젝트(`nail_energy_smooth` 등)와 카메라・라이트가 그대로 남아있으니,
Blender 안에서 바로 열어보고 다듬으셔도 됩니다.
