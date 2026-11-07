Met Calculator: files you can edit (UTF-8 text, any editor). After a change, rebuild the app.

lang_en.txt / lang_fa.txt  - all words of the app, one file per language
  [ui]       key = text           names of boxes, group titles, menu, About ...  (do not change the key)
  [a3]       code | description   dropdown a3 (pressure change)
  [e]        code | description   dropdown E (ground state, no snow)
  [eprime]   code | description   dropdown E' (ground state with snow)
  [h]        code | description   dropdown h (height of lowest cloud)
  [past]     code | description   dropdowns W1 and W2 (past weather)
  [ww]       code | description   dropdown ww (present weather)
  [iw]       code | description   dropdown IW (wind type indicator)
  [ir]       code | description   dropdown IR
  [ix]       code | description   dropdown IX
  [total]    code | description   list behind the arrow of Snow Depth (997, 998, 999 ...)
  [fresh]    code | description   list behind the arrow of Fresh Snow (1097, 1098, 1099 ...)
  To add an item, add a line to the section; to remove one, delete its line. The code (left of the |) is what goes
  into the SYNOP code. Keep the same codes in both language files.
style.txt     - fonts and sizes of text
app_config.txt - update_repo for "Update App"
fonts/        - font files (.ttf, .otf) used by style.txt
splash/       - photos for the opening screen (one per launch)
