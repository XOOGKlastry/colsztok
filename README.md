# Colsztok

Natywna aplikacja Android do pomiaru wysokości latarni aparatem i czujnikiem pochylenia. Interfejs po polsku, praca offline, bez reklam, kont i usług zewnętrznych. Dla Samsung Galaxy S22+ i innych telefonów z Androidem 8+ oraz czujnikiem grawitacji lub akcelerometrem.

## Pobierz na telefon

[Pobierz Colsztok.apk](https://github.com/XOOGKlastry/colsztok/releases/latest/download/Colsztok.apk). Otwórz APK na telefonie i zezwól przeglądarce na instalację. Po instalacji udziel aplikacji dostępu do aparatu.

## Pomiar

1. Zmierz **poziomą** odległość do osi słupa, np. 15 m, i wpisz wartość. Nie wpisuj surowej odległości ukośnej z dalmierza. Gdy dalmierz mierzy ukośnie do podstawy z tego samego punktu co aparat, D = s × cos(kąt podstawy).
2. Trzymaj telefon pionowo. Wyceluj centralnym krzyżykiem w podstawę i naciśnij **Złap podstawę**. Przycisk wymaga ustabilizowania telefonu oraz przechyłu bocznego poniżej 3°.
3. Utrzymaj środek aparatu w tym samym punkcie i skieruj krzyżyk w wybraną część oprawy. Naciśnij **Złap oprawę**. Mierz ten sam punkt we wszystkich obiektach. Wysięgnik oglądaj z boku, aby oprawa i słup były w podobnej odległości.
4. Wpisz numer/uwagi i wybierz **Zapisz pomiar**. W historii znajdziesz wynik, kąty, odległość, kalibrację i zdjęcie podglądu z siatką.
5. Eksportuj pomiary do CSV lub JSON, a zdjęcia osobno do JPEG. Dane są prywatne w pamięci aplikacji i znikają po odinstalowaniu. Eksport JSON jest archiwum; import nie jest obsługiwany w wersji 1.0.

Kolory siatki: 6 m `#FF0000`, 8 m `#FFA500`, 9 m `#FFFF00`, 12 m `#00FF00`. Siatka pojawia się po pomiarze podstawy i jest ukryta przy przechyle bocznym ponad 3°. Nie trzeba obejmować całej latarni jednym kadrem.

## Geometria i dokładność

Wynik: **H = D × (tan α_oprawy − tan α_podstawy)**. Pomiar podstawy uwzględnia wysokość aparatu i różnicę poziomów stanowiska oraz podstawy. Nie jest to jednozdjęciowa rekonstrukcja 3D: wysokość wynika z odległości i dwóch odczytów kąta czujnika; zdjęcie dokumentuje pomiar. Nie trzeba ustawiać 45° ani znać wysokości obserwatora.

Siatka korzysta z nominalnego FOV tylnego aparatu, proporcji podglądu i modelu kamery otworkowej, bez korekcji dystorsji. Może być przesunięta przez crop/stabilizację i różnicę osi aparatu oraz czujnika. **Wynik liczbowy nie korzysta z FOV.** Kamera tylna, zoom 1×; brak zmiany obiektywu w aplikacji. Fotografia jest zrzutem podglądu ze znacznikami, a nie pełną fotografią sensora. Oś tylnego aparatu musi mieć standardową orientację 90° dla telefonu w pionie.

Menu → Kalibracja poziomu: cel na tej samej wysokości co środek obiektywu. Zeruje wspólny błąd kąta. Przed pracą wykonaj co najmniej 3 pomiary znanego słupa z kilku odległości. Zapisz różnice względem wysokości referencyjnej; założenie ±0,5 m potwierdź na reprezentatywnych stanowiskach. Nie jest ono gwarantowane przez aplikację. Przy 15 m i kącie oprawy około 35° błąd 1° w samym górnym odczycie daje około 0,39 m błędu wysokości, do czego dochodzą błąd podstawy i odległości. Ruch aparatu, wiatr, przyspieszenie, źle zmierzona odległość oraz wysięgnik zmieniają wynik. Klasa 6/8/9/12 m jest przypisywana tylko w promieniu 0,5 m od wzorca, nie jest potwierdzeniem dokładności.

## Budowanie

JDK 17, Android SDK 35, Gradle 8.9, Android Gradle Plugin 8.7.3. Otwórz katalog w Android Studio lub uruchom z zainstalowanym Gradle: `gradle testDebugUnitTest lintDebug assembleDebug`. Workflow GitHub Actions wykonuje te same kroki, udostępnia APK jako artefakt i publikuje go w Releases dla tagów `v*`.

Klucz podpisu wersji deweloperskiej jest zachowany w sekrecie GitHub `COLSZTOK_SIGNING_KEY`. Aktualizacje wymagają tego samego klucza i zwiększenia versionCode. Nie dodawaj klucza do repozytorium. APK debug jest przeznaczony do instalacji bezpośredniej i testów terenowych, nie do Google Play.

## Weryfikacja

Testy jednostkowe sprawdzają geometrię dla różnych poziomów gruntu, przypadek 45°, oś tylnej kamery, projekcję siatki, klasy i niepoprawne dane. CI wykonuje również Android Lint i kompilację APK. Wymagana osobna weryfikacja aparatu, czujników, zdjęć, eksportu i pomiarów na fizycznym S22+.

Dokumentacja: [czujniki Android](https://developer.android.com/reference/android/hardware/SensorManager), [parametry kamery](https://developer.android.com/reference/android/hardware/Camera.Parameters), [AGP 8.7](https://developer.android.com/build/releases/agp-8-7-0-release-notes).
