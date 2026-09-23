# 이펙트 추가 가이드

이 문서 하나로 "Effekseer에서 이펙트 만들기 → 게임에 적용 → 위치/크기 조정 → 확인"까지 전부
끝낼 수 있도록 정리했습니다. 실제로 여러 번 삽질하면서 알아낸 함정들이라 순서대로 지켜주시면
됩니다.

## 0. 전체 그림

```
Effekseer 에디터에서 이펙트 제작
        │  File > Export > Effect Package (.efkpkg)  ← 꼭 .efkpkg로!
        ▼
vfx/ 폴더에 내보낸 파일을 쌓아둠            (임시 작업 공간, git에 안 올라가도 됨)
        │  (내가 적용할 때) 정확한 이름으로 복사
        ▼
assets/gourmet2/effeks/toriko/...   ← 실제 게임이 읽는 위치
        │  gradlew assemble
        ▼
run/logs/latest.log 에서 EffekAssetLoader 에러 확인
```

`FxLibrary.java`는 각 스킬의 연출 순간(cast)마다 **3단계로 순서대로 찾습니다**:

1. **그 스킬 전용 이펙트** (`toriko/<슬롯이름>.efkefc`) — 있으면 최우선으로 씀
2. **그 도구의 공용 형상** (`toriko/shapes/<nail|fork|knife>.efkefc`) — 1번이 없으면 이걸 씀.
   "손이 못/포크/나이프 모양이 되는" 그 결정체 이펙트가 여기 하나씩만 있고, 해당 도구를 쓰는
   모든 스킬이 공유합니다 (파일을 여러 슬롯에 중복으로 안 넣어도 됩니다).
3. **번들 CC-0 샘플** — 1, 2번 다 없으면 마지막으로 이걸 씀 (항상 존재)

즉 "포크 이펙트가 다른 것보다 특별하게 생겼으면 좋겠다" → `toriko/fork.efkefc`를 새로 만들면
됨. "그냥 포크 계열 기술이면 다 이 형상 쓰면 됨" → `toriko/shapes/fork.efkefc` 하나만
관리하면 됨. 코드 수정 없이 파일만 갖다 놓으면 자동으로 적용됩니다.

## 1. Effekseer에서 만들 때 지켜야 하는 3가지 규칙

이거 하나라도 어기면 무조건 실패합니다 (전부 실제로 겪은 문제들입니다).

### 규칙 1 — 반드시 Effekseer 1.70e 버전 사용

이 모드가 쓰는 AAA Particles는 **Effekseer 엔진 1.70e**에 고정되어 있습니다. 최신 에디터로
만들면 포맷이 안 맞아서 로드가 거부될 수 있습니다.

- [Windows 64bit](https://github.com/effekseer/Effekseer/releases/download/170e/Effekseer170eWin.zip)
- [Windows 32bit](https://github.com/effekseer/Effekseer/releases/download/170e/Effekseer170eWin_x86.zip)
- [Mac](https://github.com/effekseer/Effekseer/releases/download/170e/Effekseer170eMac.zip)

### 규칙 2 — Model(3D 모델) 노드를 쓴다면, 모델 파일을 프로젝트와 같은 폴더에 둘 것

Effekseer의 Model 노드는 `.efkefc`/`.efkpkg` 안에 모델 데이터를 통째로 내장하지 않고,
**파일 이름을 상대경로로 참조**합니다. 이때 참조 경로가 조금이라도 `../` 를 포함하면
(예: 다운로드 폴더에 있는 모델을 그냥 선택한 경우) 마인크래프트가 보안상 그 경로를
**무조건 거부**합니다 — 파일이 실제로 있어도 소용없습니다.

**해결법**: 모델 파일(`.efkmodel`, `.fbx`, `.glb`, `.obj` 등)을 다운로드 폴더 같은 데 두지
말고, **`.efkproj` 파일과 같은 폴더**로 옮긴 다음 Model 노드에서 그 위치의 파일을 다시
선택하세요. 그러면 참조 경로가 `Fork.efkmodel`처럼 순수 파일명만 남습니다 — 이게 되면 정상
작동합니다.

### 규칙 3 — `.efkpkg`로 export할 것 (`.efkefc` 단독 말고)

`.efkpkg`는 참조하는 리소스를 알아서 찾아서 zip으로 묶어주는 패키지 형식입니다.
`File > Export > Effect Package (.efkpkg)`로 내보내세요. 스프라이트/링 같은 단순 이펙트만
쓰고 Model 노드가 전혀 없다면 `.efkefc` 단독으로도 되지만, 헷갈리지 않게 그냥 항상
`.efkpkg`로 통일하는 걸 추천합니다.

> ⚠️ `.efkpkg`로 내보내도 규칙 2를 어겼다면 여전히 실패합니다 — `.efkpkg`는 Effekseer 자신이
> "이 리소스가 프로젝트에 속해있다"고 인식한 것만 챙겨줍니다. `../Downloads/...` 같은 외부
> 참조는애초에 의존성 목록에 잡히지도 않습니다.

## 2. 이름표 — 뭘 어디에 넣어야 하나

### 2-1. 도구 공용 형상 (`toriko/shapes/...`)

"손이 이 도구 모양이 된다"를 나타내는 결정체 이펙트입니다. 도구별로 **딱 하나씩만** 있고,
그 도구를 쓰는 모든 스킬이 같이 씁니다.

| 파일 | 지금 쓰는 스킬 |
|---|---|
| `toriko/shapes/nail.efkefc` | 네일 펀치, 네일 건, 13연타 락온 |
| `toriko/shapes/fork.efkefc` | 포크, 플라잉 포크 |
| `toriko/shapes/knife.efkefc` | 나이프, 레그 나이프, 플라잉 나이프 |

현재 세 파일 다 ✅ 적용되어 있습니다 (`raw_models/effekseer_weapon_shards_smooth/`의 Blender
모델로 만든 것들).

### 2-2. 스킬별 슬롯 (`toriko/<슬롯이름>.efkefc`)

이 슬롯에 파일을 넣으면 위의 공용 형상보다 **우선** 적용됩니다 — "이 스킬만은 특별한 연출을
쓰고 싶다"할 때 씁니다. `EffekIds.Custom`(코드) ↔ 이 표 ↔ 실제 파일이 전부 같은 이름을 씁니다.

| 슬롯 이름 (`toriko/...`) | 어느 스킬의 어느 순간 | 지금 없으면 뭐가 대신 나오나 |
|---|---|---|
| `nail_punch` | 네일 펀치 시전 | `shapes/nail` |
| `nail_impact` | 네일 펀치/네일 건 착탄 | ✅ 적용됨 (전용, 형상과 무관) |
| `nail_gun` | 네일 건 시전(연사) | `shapes/nail` |
| `nail_finish` | 13연타 피니셔 | ✅ 적용됨 (전용, 형상과 무관) |
| `thirteen_lock` | 13연타 락온 순간 | `shapes/nail` |
| `fork` | 포크 시전 | `shapes/fork` |
| `fork_impact` | 포크/플라잉 포크 착탄 | 번들 샘플(`ToonHit`) — 아직 커스텀 없음 |
| `knife_slash` | 나이프 시전 | `shapes/knife` |
| `knife_impact` | 나이프류 착탄 | 번들 샘플(`ToonHit`) |
| `leg_knife` | 레그 나이프 회전 베기 | `shapes/knife` |
| `flying_fork_cast` | 플라잉 포크 발사 순간 | ✅ 적용됨 (전용 — 지금은 `shapes/fork`와 같은 내용, 독립적으로 교체 가능) |
| `flying_fork_trail` | 플라잉 포크 비행 궤적 (=투사체 본체 외형) | ✅ 적용됨 (전용 — 위와 동일) |
| `flying_knife_cast` | 플라잉 나이프 발사 순간 | ✅ 적용됨 (전용 — 지금은 `shapes/knife`와 같은 내용, 독립적으로 교체 가능) |
| `flying_knife_trail` | 플라잉 나이프 비행 궤적 (=투사체 본체 외형) | ✅ 적용됨 (전용 — 위와 동일) |
| `intimidation` | 위압・식욕의 악마 오라 | 번들 샘플(`Aura01`) |
| `intimidation_pulse` | 위압 발동 파동 | 번들 샘플(`Barrior02`) |
| `food_immersion` | 식몰 채널링 | 번들 샘플(`Light`) |
| `food_immersion_burst` | 식몰 완료 폭발 | 번들 샘플(`Barrior01`) |
| `awakened_aura` | 각성 상태 지속 오라 | 번들 샘플(`Aura01`) |
| `combat_impact` | 컴뱃 모드 타격 착탄 — 기본 공격·띄우기·내리꽂기 전부 | 번들 샘플(`ToonHit`) + 링 |

`nail_punch`/`nail_gun`/`thirteen_lock`처럼 지금 "형상으로 폴백 중"인 슬롯은 게임에서
정상적으로 못 모양 이펙트가 보입니다 — 그것과는 별개로 **그 스킬만의** 연출을 원하면 저
슬롯 이름으로 파일을 넣으면 형상보다 우선해서 그게 나옵니다.

새 이름이 필요하면(새 스킬 추가 등)
[`EffekIds.java`](../src/main/java/org/example/hanjinwoo/gourmet2/client/fx/EffekIds.java)의
`Custom`(스킬 전용) 또는 `Shape`(도구 공용) 안에 상수를 추가하고, `FxLibrary.java`에서
참조하면 됩니다.

## 3. 적용하는 법

1. `vfx/` 폴더에 export한 `.efkpkg`(와 Model을 썼다면 그 옆의 원본 폴더)를 넣어주세요.
2. 저(Claude)에게 "export했다"고 알려주시면서, **어느 도구 공용 형상**(못/포크/나이프)인지
   아니면 **특정 스킬 전용**(예: "나이프 시전만 따로")인지 말씀해주시면, 정확한 위치로 복사하고
   `gradlew assemble`로 빌드 확인까지 해드립니다.
3. 직접 하고 싶으시면:
   - 도구 공용 형상이면 → `src/main/resources/assets/gourmet2/effeks/toriko/shapes/<nail|fork|knife>.efkpkg`
   - 특정 스킬 전용이면 → `src/main/resources/assets/gourmet2/effeks/toriko/<슬롯이름>.efkpkg`
   - (Model을 쓴 경우, `.efkmodel` 파일도 **`.efkpkg`/`.efkefc`와 같은 폴더 안에** 원래
     파일명 그대로 같이 넣어주세요 — 규칙 2 참고)

## 4. 위치・크기 조정하기

전부 [`FxLibrary.java`](../src/main/java/org/example/hanjinwoo/gourmet2/client/fx/FxLibrary.java)
안에서 조정합니다. 스킬마다 이런 식으로 정의돼 있습니다:

```java
define(SkillFx.FORK_CAST,
        FxPart.onBody(0.20F, RIGHT_HAND, Custom.FORK, Shape.FORK, Sample.LASER_02));
```

`Custom.FORK` → `Shape.FORK` → `Sample.LASER_02` 순서로 먼저 있는 걸 씁니다 (2절의 3단계
우선순위와 동일).

- **첫 번째 숫자(`0.20F`)** = 크기 배율. 작을수록 작게 보입니다. (모델이 원래 크게 만들어져
  있어서 대부분 0.15~0.5 사이로 꽤 작게 잡혀있습니다.)
- **두 번째 인자(`RIGHT_HAND`)** = 붙는 위치. 파일 위쪽에 정의된 상수들:
  - `RIGHT_HAND` — 캐릭터 오른쪽, 주먹 높이 (팔에서 나가는 기술용)
  - `FEET` — 발밑 중앙 (레그 나이프처럼 사방으로 퍼지는 기술용)
  - `TORSO` — 몸통 중앙, 조금 위 (오라처럼 몸 전체를 감싸는 기술용)
  - 새 위치가 필요하면 `new Vec3(좌우, 위아래, 앞뒤)`로 직접 만들면 됩니다 (오른쪽/위/앞이
    +방향, 단위는 블록)
- **`onBody` vs `onHead` vs `onProjectile` vs `world`**:
  - `onBody` — 캐릭터 몸통의 **좌우 방향만** 따라감 (상하는 항상 수평 취급). 서있는/걷는
    캐릭터에 붙는 대부분의 경우 이거 씁니다.
  - `onHead` — 캐릭터가 보는 방향 기준(고개를 들면 이펙트도 따라 위로 감) — 위아래로 홱홱
    움직여서 웬만하면 안 씁니다.
  - `onProjectile` — 위치는 매 프레임 따라가되, **방향(좌우+상하 전부)은 발사된 순간에 고정**.
    똑바로 날아가서 다시 회전할 일이 없는 투사체용 — `flying_fork_trail`/`flying_knife_trail`이
    이걸 씁니다. `onBody`를 썼으면 위아래로 던져도 이펙트가 항상 수평으로 나왔을 겁니다.
  - `world` — 특정 좌표에 고정. 타격 이펙트(`_impact`)처럼 맞은 지점에 뜨는 것들이 이걸 씁니다.

**예시**: 나이프 이펙트가 너무 크면 `KNIFE_CAST` 줄의 `0.40F`를 `0.25F`로,
더 팔 쪽으로 붙이고 싶으면 `RIGHT_HAND`의 첫 번째 숫자(좌우 오프셋)를 `0.45`에서 `0.6` 정도로
올려달라고 말씀해주시면 됩니다.

## 5. 확인하는 법

빌드하고 나서 `gradlew runClient`(또는 VSCode "Client" 실행 구성)로 게임을 켜고 스킬을 한 번
써본 뒤, 다음을 확인합니다:

```
run/logs/latest.log 에서 "EffekAssetLoader" 로 검색
```

- 아무것도 안 나오면 정상 (전부 로드 성공).
- `Failed to load gourmet2:toriko/...` 가 있으면 그 슬롯이 실패한 것 — 아래 체크리스트 참고.

## 6. 문제 해결 체크리스트

| 로그에 이렇게 나오면 | 원인 | 해결 |
|---|---|---|
| `Invalid path ...Downloads/....efkmodel: Invalid segment '..'` | 모델을 프로젝트 바깥(다운로드 폴더 등)에서 참조 중 | [규칙 2](#규칙-2--model3d-모델-노드를-쓴다면-모델-파일을-프로젝트와-같은-폴더에-둘-것) 참고, 모델을 프로젝트 폴더로 옮기고 다시 연결 후 재export |
| `Failed to load gourmet2:toriko/X` (예외 스택트레이스 없이 이 줄만) | 네이티브 Effekseer 엔진이 파일 자체를 거부 (보통 에디터 버전 문제) | [규칙 1](#규칙-1--반드시-effekseer-170e-버전-사용) 확인 |
| 이펙트가 안 보이는데 로그에도 에러가 없음 | `FxLibrary`의 폴백 후보 중 더 앞순위가 깨져서 그걸 "찾긴 찾았는데" 재생이 조용히 실패하는 경우 (예: `HANMADO_HIT` 샘플처럼 내부적으로 깨진 파일) | 해당 `FxPart`의 후보 목록에서 문제되는 샘플을 빼기 |
| 이펙트가 캐릭터 정중앙에 크게 뜸 | 앵커가 `TORSO`/`onHead`로 잡혀있거나 스케일이 큼 | [4. 위치・크기 조정](#4-위치크기-조정하기) 참고 |
| `vfx/`에 넣었는데 반영이 안 됨 | 슬롯 이름으로 복사하는 단계가 빠짐 (`vfx/`는 실제 게임이 읽는 위치가 아님) | [3. 적용하는 법](#3-적용하는-법) 참고 |

## 7. 참고 — 지금 쓰이는 원본 모델

`raw_models/effekseer_weapon_shards_smooth/`에 Blender로 만든 매끄러운 못/포크/나이프
결정체 모델(`.obj`/`.glb`)이 있습니다. 지금 `toriko/shapes/nail.efkefc` /
`toriko/shapes/fork.efkefc` / `toriko/shapes/knife.efkefc`가 이 모델들을 Effekseer의
Model 노드로 가져와서 만든 이펙트입니다. 같은 계열로 새 이펙트를 만들고 싶으면 이 폴더의
모델을 재사용하시면 됩니다.

## 8. 참고 — 플라잉 포크/나이프는 렌더러가 없습니다

`FlyingForkEntity`/`FlyingKnifeEntity`는 일부러 **Blockbench 모델을 그리지 않습니다**
(`FlyingForkRenderer`/`FlyingKnifeRenderer`가 사실상 빈 껍데기입니다). 눈에 보이는 형태는
전부 `flying_fork_trail`/`flying_knife_trail` 슬롯의 Effekseer 이펙트가 담당합니다 —
`SkillContext`에서 `FxDispatch.on(level, fx, entity)`로 엔티티에 붙여서, 매 프레임 그
엔티티 위치를 따라다니게 만드는 방식입니다 (`ParticleEmitterInfo.bindOnEntity`).

이렇게 한 이유: 충돌 판정・데미지・블록 파괴는 전부 진짜 마인크래프트 Entity(`SkillProjectile`)
쪽에서 처리되고 렌더링과는 완전히 무관하기 때문에, 시각적으로는 매끄러운 Effekseer 결정체를
쓰면서 상호작용은 그대로 다 가져갈 수 있습니다. 새로운 투사체 기술을 추가할 때도 이 패턴을
따르면 됩니다 — Blockbench 모델 만들 필요 없이 `toriko/shapes/`의 형상을 재사용하거나
새 형상을 하나 추가하면 됩니다.

**약한 블록 파괴**: 두 투사체 다 날아가다 나뭇잎・유리(블록/판유리)・농작물・거미줄을 만나면
그 자리에서 부수고 계속 날아갑니다 (`Hurt.breakIfWeak`) — 그 외의 단단한 블록은 예전처럼
그 자리에서 멈춥니다. `Config.cutVegetation`이 꺼져있으면 이 동작도 같이 꺼집니다.

**전용 이펙트**: `flying_fork_cast`/`flying_fork_trail`/`flying_knife_cast`/`flying_knife_trail`
슬롯이 등록되어 있습니다 (2-2절 표 참고) — 지금은 각각 `shapes/fork`/`shapes/knife`와 같은
내용이 복사돼 있지만, 나중에 "날아갈 때만 더 길쭉하게" 같은 전용 연출을 만들면 저 슬롯
이름으로 넣어서 손에 붙는 형상과 독립적으로 바꿀 수 있습니다.

**좌우로 흔들리던 문제**: `ModEntities.java`에서 두 투사체의 `updateInterval`을 `1`(매 틱
서버 동기화)에서 `10`로 낮췄습니다 — 바닐라 화살(20)・달걀/파이어볼(10)과 같은 값입니다.
투사체는 클라이언트와 서버가 각자 같은 직선 운동을 독립적으로 계산하는 구조라, 서버 보정을
매 틱 보내면 그게 클라이언트 자체 계산과 매 틱 충돌하면서 흔들림으로 보였던 것으로
보입니다 — 드물게만 보정하는 게 오히려 더 안정적입니다.
