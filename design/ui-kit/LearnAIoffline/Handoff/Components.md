# Component library

| Component | Anatomy and sizing | States | Compose implementation |
|---|---|---|---|
| Filled / outlined / text buttons | Label; optional leading icon. 48 dp touch target, 12–16 dp horizontal inset. | Enabled, pressed, focused, loading, disabled. | Button / OutlinedButton / TextButton. Disable double submission. Give Cancel and destructive actions distinct labels. |
| Topic card | Family tile, topic name, lesson count, progress. 16 dp inset, 16 dp corners. | New, in progress, complete, pressed. | OutlinedCard. Use the whole card as one semantic target. Combine icon shape and title with colour. |
| Continue card | Course context, lesson title, minutes, resume action. 18–22 dp radius. | First lesson, saved position, complete. | Card and filled button. Place an illustrative vector lattice beside text. Remove artwork before truncating the lesson name. |
| Metric card | Value, unit, label. 72 dp minimum height. | Zero, measured, unavailable. | Surface with labelMedium and titleLarge. Display a dash for accuracy before any answers. |
| Mode card | Icon, mode, one-line explanation, trailing affordance. 60 dp minimum. | Available, selected, empty pool. | ListItem or selectable Card. Explain an empty pool inline. |
| Filter / choice chips | Label and optional check, 32 dp visual minimum. | Active, inactive, disabled. | FilterChip / SingleChoiceSegmentedButtonRow. Provide a 48 dp hit area; wrap at larger font scales. |
| Progress bar / ring | Fraction plus textual equivalent. 6 dp bar, 96–128 dp ring. | Zero, partial, complete, indeterminate. | LinearProgressIndicator / CircularProgressIndicator; expose progress semantics and an exact count. |
| Quiz options | Letter, wrapping answer, state icon and feedback. 52 dp minimum. | Default, selected, submitted correct, submitted wrong, locked. | selectableGroup with Role.RadioButton. Outline selected option with primary. Show check and “Correct” or cross and “Your answer”. Keep locked text legible. |
| Code block | Language tag, copy action, preserved monospace content. 14/22 sp, 16 dp inset. | Light/dark code, copied, long lines. | AnnotatedString spans, SelectionContainer and horizontalScroll. Copy raw source; announce “Code copied” using snackbar. |
| Callout | Label, optional icon, body; 16 dp inset. | Note, tip, warning. | Surface with secondaryContainer or tertiaryContainer paired with its on-colour. |
| Lesson table | Header cells and aligned data cells; at least 12 dp padding. | Short, wide, empty cells. | Single horizontally scrolling column of shared-width rows. Use header semantics and a vertically stacked alternative at large text. |
| Flashcard | Topic, position, term or definition, reveal/advance. 220 dp minimum. | Front, back, last, empty. | AnimatedContent crossfade or short Y-axis flip; expose one accessible side at a time. |
| Bottom navigation | Four icons and persistent labels; 64–80 dp plus system inset. | Selected, unselected, restored tab. | NavigationBar / NavigationBarItem, one saved stack per tab. Hide during focused reading and quizzes. |
| Top app bar | Root title or wordmark, relevant actions. 56–64 dp plus inset. | Root, detail/back, collapsed. | TopAppBar; Home includes day/night and Settings. Place Bookmarks only on saveable details; the root catalogue exposes the saved collection. |
| Search field | Magnifier, editable query, clear button. 52–56 dp. | Idle, typing, results, no results. | OutlinedTextField, singleLine, IME Search. Query the local index off the main thread. |
| Dialog | Title, impact, cancel, confirm; maximum width 560 dp. | Open, dismiss, committing, failure. | AlertDialog. Reset needs an explicit destructive affordance and retains focus on dismissal. |
| Bottom sheet | Handle, title, choices, optional Apply. 24 dp upper corners. | Partial, expanded, selected, dismiss. | ModalBottomSheet. Use for index, filters, theme and reader settings. Preserve pending filters on rotation. |
| Snackbar | Single message plus optional Undo. 48 dp minimum. | Visible, action, timeout. | SnackbarHost at Scaffold level; honor accessibility-adjusted timeout. |
| Day/night switch | Sun/crescent in a 48 dp hit target. | Follows resolved system, explicit light, explicit dark. | Use DayNightSwitch.kt and a single theme preference for both locations. |
| Empty / error / loading | Meaningful symbol, heading, context and one recovery action. | First use, filtered empty, failed local read, loading. | Keep background stable; show indeterminate progress for actual loading. Retry preserves prior progress. |
