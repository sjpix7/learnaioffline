# Screen specification

The gallery and paired PNGs define filled light and dark layouts. Each list reads local data, shows its neutral loading state on first read and preserves the previous content during refresh. Empty study history, activity and stats show zero with a Start learning action; empty result pools lead back to filters or setup. Content errors use Retry. Settings and informational screens can render immediately from defaults. Secondary lists open from their owning tab. Back returns to the prior scroll position. Modal previews are expanded for inspection; implement the index and appearance selectors as sheets and reset as a dialog.

| Screen | Navigation context | Top to bottom | State / interaction details |
|---|---|---|---|
| Home | Home | Date, greeting, streak, continue card, resume, daily goal, topic grid, activity strip | First visit uses Start learning and a 0-minute goal. Returning learners see the saved topic, lesson and scroll anchor. |
| Learn | Learn | Title, count, search, filter chips, topic list, glossary | Local index loading uses three skeleton rows. Empty filters link to All topics. |
| Topic | Learn | Topic hero, course progress, lesson TOC with states and counts | New topics start at lesson one. Checked rows remain reviewable. Content failure offers Retry. |
| Lesson | Focused flow | Topic and lesson index, title, read time, index sheet, body, code, note, table, list, pinned lesson navigation | Restore reading position; final lesson changes Next to Topic overview. Long code scrolls horizontally. |
| Lesson index | Focused flow | Section list and jump actions | Use the shared loading, empty and populated patterns where applicable. |
| Glossary | Learn | Count, flashcards CTA, concepts and topic labels | Search and topic filter narrow the list. A concept opens its definition, example and source lesson. |
| Flashcards | Focused flow | Position, progress, front face, reveal | Flip by tapping or accessible button. Announce the exposed side. |
| Flashcard answer | Focused flow | Position, progress, definition, reverse, next | Flip by tapping or accessible button. Announce the exposed side. |
| Practice | Practice | Question count, quick set, seven practice modes, bank entry | Use the shared loading, empty and populated patterns where applicable. |
| Quiz setup | Focused flow | Topic, mode, quantity, start | Disable Start for empty pools. By lesson reveals lesson selector; From number reveals range-checked input. Cap set size to available items. |
| Quiz question | Focused flow | Set counter, progress, prompt, optional code, four choices, hint, submit | Require a selection before submit. Feedback locks choices, identifies correct answer and opens the source lesson without losing the set. |
| Quiz hint | Focused flow | Question, choices, hint and sample code, submit | Require a selection before submit. Feedback locks choices, identifies correct answer and opens the source lesson without losing the set. |
| Quiz correct | Focused flow | Question, locked choices, correct state, explanation, lesson, next | Require a selection before submit. Feedback locks choices, identifies correct answer and opens the source lesson without losing the set. |
| Quiz wrong | Focused flow | Question, chosen and correct labels, explanation, lesson, next | Require a selection before submit. Feedback locks choices, identifies correct answer and opens the source lesson without losing the set. |
| Quiz summary | Focused flow | Score ring, accuracy, time, mistakes, retry, finish | Perfect scores show a completion message and another set. |
| Question bank | Practice | Topic/lesson/type and answer-state filters, count, paged questions | Combine topic, lesson, question type and answer-state filters. Empty results offer Clear filters. |
| Progress | Progress | Streak, accuracy, time, weekly bars, topic rows, history, bookmarks | Use the shared loading, empty and populated patterns where applicable. |
| Topic progress | Progress | Topic title, completion, per-lesson stats | Use the shared loading, empty and populated patterns where applicable. |
| History | Progress | Month, activity cells, daily rows | Use the shared loading, empty and populated patterns where applicable. |
| Day detail | Progress | Date, metrics, topic sessions, lessons | Use the shared loading, empty and populated patterns where applicable. |
| Bookmarks | Learn | Kind filter, saved items | Zero saved items opens Bookmarks empty. Removal gives an Undo snackbar. |
| Search | Learn | Editable query, kind filters, grouped results | Idle shows suggested topics; searching reads the offline index; unmatched terms open No results. |
| Settings | Focused flow | Appearance, Reading, Practice, Study, Data, About | Use the shared loading, empty and populated patterns where applicable. |
| Theme chooser | Focused flow | Theme mode, accents, dynamic and AMOLED, done | Use the shared loading, empty and populated patterns where applicable. |
| About | Focused flow | Version, privacy, licence and contact links | Ship licences and privacy text locally. Contact opens an external mail app only on request. |
| Onboarding 1 | Focused flow | Offline intro, continue or skip | Use the shared loading, empty and populated patterns where applicable. |
| Onboarding 2 | Focused flow | Practice intro, continue or skip | Use the shared loading, empty and populated patterns where applicable. |
| Onboarding 3 | Focused flow | Daily goal choices, continue or skip | Use the shared loading, empty and populated patterns where applicable. |
| Onboarding 4 | Focused flow | Theme choice, start learning | Use the shared loading, empty and populated patterns where applicable. |
| Splash | Launch | Centered adaptive mark on solid colour | Use the platform launch window; dismiss on first ready frame. |
| Bookmarks empty | Learn | Bookmark symbol, empty collection copy, browse lessons | Use the shared loading, empty and populated patterns where applicable. |
| No results | Learn | Search symbol, query guidance, clear filters | Use the shared loading, empty and populated patterns where applicable. |
| Content error | Learn | Local content issue, retained progress, retry | Use the shared loading, empty and populated patterns where applicable. |
| Loading | Learn | Local index status and loading indicator | Use the shared loading, empty and populated patterns where applicable. |
| Reset confirmation | Focused flow | Impact, keep progress, destructive confirm | Use the shared loading, empty and populated patterns where applicable. |
| Unlock full version | Focused flow | Edition summary, entitlements, local price, continue free | Optional build only. Fetch localized price before confirmation. Handle pending, cancelled, offline and restored purchase. Do not display in fully free edition. |
