# brief

The Brief section's engine: the doors and questions that help a person find the visual language
they are aiming for (the ethos), and what the app does with it. It turns the answers into target
ranges taken from the style atlas, finds the real fonts nearest to them, spots where the answers
pull against each other, measures the project's own letters against the targets, and writes the
guide: what to draw next, how, and why.

Pure Kotlin (JVM and Wasm), no UI. The answers are stored in the project (`project`'s `Ethos`);
the numbers are always derived from `qa:corpus`'s style atlas at the time they are shown.
