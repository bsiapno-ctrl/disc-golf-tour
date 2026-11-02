# Course data sources

Hole numbers below are lengths in feet and pars read from public course pages (own wording; no maps or images copied).
Geometry in the JSON (tee/basket positions, bends, trees, ponds, terrain) is still an approximation: each hole keeps its
old direction and was rescaled so tee-to-basket distance matches the real length.

## Real lengths and pars

* **Picnic Island** (Tampa, FL): UDisc layout "Orange Pads/Orange Baskets", par 57, 4,075 ft.
  https://udisc.com/courses/picnic-island-30nO . Disc Golf Course Review lists par 60 over A/B/C pins (two par 4s on A,
  seven on C); per-hole numbers for other pins not found. Notes: tide defines OB on two bayside fairways, 2 water hazards,
  wind matters, mostly flat.
* **Olympus** (Brooksville, FL): UDisc layout "Red to Red", par 61, 5,920 ft (13 layouts exist, incl. 24-hole long course).
  https://udisc.com/courses/olympus-dgc-u7x5 . Hosts DGPT Supreme Flight Open (2026 schedule:
  https://discgolffanatic.com/2026-disc-golf-pro-tour-schedule/). No per-hole OB/water notes found.
* **Cypress Point Park** (Tampa, FL): UDisc layout "Short Pads", par 64, 6,525 ft.
  https://udisc.com/courses/cypress-point-park-eSXz . PDGA directory: 18 holes, 7,980 ft longest, Climo/King 2014.
  https://www.pdga.com/course-directory/course/cypress-point-park-disc-golf-course . The "Cypress Long" layout
  (par 64, 7,393 ft) was used for MPO in the 2023 Gulf Coast Tour Challenge; per-hole numbers for it not found.

## Which Cypress is on the Pro Tour?

None found. Cypress Point Park hosted the Gulf Coast Tour Challenge (PDGA Pro/Am C-Tier, 2022-2024), which is not DGPT:
https://www.pdga.com/tour/event/65993 , https://www.pdga.com/tour/event/79430 . The 2026 DGPT schedule lists no Tampa
or Cypress event; its only Florida stop is the Supreme Flight Open at Olympus, Brooksville. The "Tampa Cypress" the user
may mean is probably Cypress Point Park (the only Tampa Cypress disc golf course found); the Pro Tour Florida course is Olympus.

## Still invented

blue-ribbon-pines-inspired, idlewild-inspired, maple-hill-inspired, smugglers-notch-inspired: no usable per-hole data
was found (Blue Ribbon Pines on UDisc is a 27-hole layout set, not a plain 18). Their pars and lengths are made up;
the layout description says UNVERIFIED, INVENTED.

## Hole shaping (doglegs, blockers, hazards)

Every bundled course except Picnic Island was reshaped so that holes are not all straight tee-to-basket shots: doglegs
(via points with trees filling the inside corner), S-curves, tall blocking trees, bush walls, a few ponds and out-of-bounds
strips, tight tree tunnels. This is INVENTED shaping done by tools/shape_holes.py. It is not taken from the real
courses: for Olympus and Cypress Point Park only the lengths, pars and approximate tee/basket positions stay as described
above (tee-to-basket distance and par are unchanged; the playing line is a few percent longer on doglegs), and the water,
OB and bends there should not be read as real features. The four "inspired" courses were already invented.
