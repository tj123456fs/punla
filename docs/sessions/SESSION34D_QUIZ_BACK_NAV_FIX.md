# Session 34d — Quiz Back Navigation Fix

## Symptom
After finishing a quiz, tapping **Back to quiz** could crash the app. The quiz detail back path could be vulnerable to the same issue.

## Cause
`QuizScreen` animates between library/detail/take modes with Compose `Crossfade`. The outgoing branch remains composed briefly during the animation. Navigation state was cleared immediately, while the outgoing `take`/`detail` branch still used `runRequest!!` and `selectedQuiz!!`. A recomposition during that window could therefore throw a null-pointer exception.

## Fix
The animated branches now capture nullable navigation state and render only when the corresponding state is still present. There are no force unwraps of `runRequest` or `selectedQuiz` in those transition branches.

## Scope
This is additive to the Session 34c quiz-attempt save lifecycle fix.
