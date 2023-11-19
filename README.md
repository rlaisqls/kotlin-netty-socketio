# gribouille

kotlin으로 netty socketio 라이브러리를 구현 및 개선해보는 프로젝트입니다.

## 목표

- [x] netty 라이브러리를 직접 사용해봄으로써 낮은 추상화 수준의 자바 네트워킹 경험해보기
- [ ] ~~kotlin coroutine을 활용하여 최적화하기~~
  - Coroutine을 사용했을 때 Continuation으로 인해 메모리를 더 많이 사용하기에 대용량 트래픽 처리시 Netty의 순수한 NIO보다 성능이 떨어지는 것으로 판단되어 진행하지 않았습니다. (artillery로 부하 생성 및 intellij Profiling tools로 JVM, 스레드 모니터링)
- [x] 클래스간 복잡한 의존성을 가지고 있는 기존 아키텍처 개선하기 -> object 기반 싱글톤 구조로 변경

## class diagram

![DisconnectableHub](https://github.com/rlaisqls/gribouille/assets/81006587/7e70c549-6f2f-4573-bf51-223cd04281db)

## 배운점 정리

### netty

netty로 구성되어있는 코드의 전체 구조를 이해하고, 비동기 처리를 위한 스레드 동작을 파악하기 위해 netty framework에 대해 공부하였습니다.
- [netty 서버 예제](https://github.com/rlaisqls/TIL/blob/main/%EA%B0%9C%EB%B0%9C/netty/netty%E2%80%85server%E2%80%85%EC%98%88%EC%A0%9C.md)
- [HashedWheelTimer](https://github.com/rlaisqls/TIL/blob/main/%EA%B0%9C%EB%B0%9C/netty/HashedWheelTimer.md)
- [netty의 Thread 모델](https://github.com/rlaisqls/TIL/blob/main/%EA%B0%9C%EB%B0%9C/netty/netty%EC%9D%98%E2%80%85thread%E2%80%85%EB%AA%A8%EB%8D%B8.md)
- [netty 메시지 전송 과정](https://github.com/rlaisqls/TIL/blob/main/%EA%B0%9C%EB%B0%9C/netty/netty%E2%80%85%EB%A9%94%EC%8B%9C%EC%A7%80%E2%80%85%EC%A0%84%EC%86%A1%E2%80%85%ED%9D%90%EB%A6%84.md)

### varience

다양한 타입의 제네릭을 가질 수 있는 `DataListener<T>`를 `List<DataListener<*>>`로 저장한 후 꺼내왔을때, kotlin에선 class를 알 수 없어 `Nothing`으로 표기되는 문제가 발생했습니다. 이를 통해 `<*>`과 `<?>`의 차이점과 [Mixed-Site Variance](https://rosstate.org/publications/mixedsite/) 등 다양한 공변성 방식이 있음을 알게 되었습니다.
- [자바<?>와 코틀린<*>](https://github.com/rlaisqls/TIL/blob/main/%EC%96%B8%EC%96%B4%E2%80%85Language/%EC%9E%90%EB%B0%94%3C%EF%BC%9F%3E%EC%99%80%E2%80%85%EC%BD%94%ED%8B%80%EB%A6%B0%3C*%3E.md)

### coroutine

- [코루틴](https://github.com/rlaisqls/TIL/blob/main/개발/비동기/coroutine/코루틴.md)
- [Channel](https://github.com/rlaisqls/TIL/blob/main/개발/비동기/coroutine/Channel.md)
- [Coroutine CPS](https://github.com/rlaisqls/TIL/blob/main/개발/비동기/coroutine/Coroutine%E2%80%85CPS.md)
- [Coroutine Delay](https://github.com/rlaisqls/TIL/blob/main/개발/비동기/coroutine/Coroutine%E2%80%85Delay.md)
- [Coroutine Dispatcher](https://github.com/rlaisqls/TIL/blob/main/%EA%B0%9C%EB%B0%9C/%EB%B9%84%EB%8F%99%EA%B8%B0/coroutine/Coroutine%E2%80%85Dispatcher.md)
- [Coroutine Scope, Context](https://github.com/rlaisqls/TIL/blob/main/개발/비동기/coroutine/Coroutine%E2%80%85Scope,%E2%80%85Context.md)


## 참고 자료

- [책: 네티 인 액션](http://www.yes24.com/Product/Goods/25662949)
- [netty java doc](https://netty.io/5.0/api/index.html)
- [kotlin docs](https://kotlinlang.org/docs/home.html)
