"""Olympus DGC (Brooksville FL) hole data.

RAW: tee / basket / dogleg pins copied from the UDisc course-map page data (udisc.com/courses/olympus-dgc-u7x5/v2/course-map,
the page's embedded react-router state).  Each point is (a, b) = (round((lat-28.55)*1e5), round((lon+82.30)*1e5)).
Per hole: mpo = Gold tee -> Blue basket with its UDisc bends; red = Red tee -> Red basket with its bends.
vid = tournament length in metres read off the Jomez DiscGolf title card of the 2026 Supreme Flight Open (None = not seen).

STYLE: what the two Jomez videos (MPO R1F9 front nine, MPO R1B9 back nine; player-eye footage, no drone flyovers) show along each
hole.  Positions are along the MPO line: u = 0 tee .. 1 basket, v = metres to the RIGHT of the direction of play (negative = left).
  fw      mown fairway half... full width in metres (hole 'fairwayWidth')
  L, R    (spacing m, depth m) of the tree belt on each side; smaller spacing = denser.  Zones (u0,u1,side,factor) thin it out
          (factor 99 = open) or thicken it (factor < 1)
  oak     fraction of belt trees that are big spreading live oaks (rest: slash/longleaf pine)
  trees   named trees (u, v, kind, height, canopy radius); kind: oak | pine | scrub
  bushes  wiregrass / palmetto clumps (u, v, radius)
  elev    absolute heights (metres, arbitrary zero) at stations u along the line (ESTIMATES, see docs/COURSE_SOURCES.md)
  bumps   ground features (u, v, dh, radius) added to the height grid
  note    one-line play note
  conf    confidence label for the notes
"""

RAW = {
 1:  dict(mpo=((629,-803), [], (655,-912)), red=((629,-840), [], (640,-902)), vid=106),
 2:  dict(mpo=((664,-931), [(566,-934),(495,-908)], (457,-914)), red=((570,-892), [(522,-912)], (498,-923)), vid=239),
 3:  dict(mpo=((448,-932), [(379,-912)], (364,-831)), red=((434,-934), [(379,-912)], (364,-840)), vid=165),
 4:  dict(mpo=((414,-841), [(375,-771)], (374,-647)), red=((371,-781), [], (383,-703)), vid=220),
 5:  dict(mpo=((402,-647), [(465,-751)], (435,-805)), red=((412,-673), [], (460,-736)), vid=192),
 6:  dict(mpo=((421,-866), [], (507,-832)), red=None, vid=98),
 7:  dict(mpo=((504,-874), [], (601,-876)), red=((521,-870), [], (594,-884)), vid=110),
 8:  dict(mpo=((553,-841), [], (517,-713)), red=((547,-792), [], (528,-727)), vid=136),
 9:  dict(mpo=((494,-681), [], (402,-599)), red=((478,-656), [], (420,-618)), vid=146),
 10: dict(mpo=((381,-618), [], (382,-498)), red=((382,-594), [], (379,-514)), vid=113),
 11: dict(mpo=((365,-462), [(391,-372),(436,-371)], (455,-359)), red=((362,-432), [(391,-372)], (420,-357)), vid=166),
 12: dict(mpo=((390,-481), [(492,-406)], (499,-367)), red=((417,-471), [(488,-415),(504,-380)], (520,-368)), vid=162),
 13: dict(mpo=((556,-381), [], (665,-365)), red=((571,-383), [], (649,-358)), vid=138),
 14: dict(mpo=((654,-392), [(571,-450),(504,-447)], (476,-465)), red=((588,-434), [(503,-448)], (443,-488)), vid=None),
 15: dict(mpo=((422,-522), [], (675,-475)), red=((483,-533), [], (653,-479)), vid=293),
 16: dict(mpo=((672,-532), [(695,-669),(669,-699)], (669,-725)), red=((711,-579), [(695,-668)], (666,-696)), vid=None),
 17: dict(mpo=((650,-622), [(604,-596),(588,-563)], (516,-573)), red=((643,-608), [], (602,-621)), vid=168),
 18: dict(mpo=((518,-626), [(571,-744)], (642,-766)), red=((525,-699), [(576,-740)], (590,-813)), vid=None),
}
PAR_MPO = {1:3,2:4,3:4,4:4,5:4,6:3,7:3,8:3,9:3,10:3,11:4,12:3,13:3,14:5,15:5,16:4,17:4,18:4}
PAR_RED = {1:3,2:3,3:4,4:3,5:3,6:3,7:3,8:3,9:3,10:3,11:4,12:4,13:3,14:4,15:4,16:4,17:3,18:4}

STYLE = {
 1: dict(fw=18, L=(10,22), R=(10,22), oak=.5, conf='medium',
   elev=[(0,30.0),(.15,29.0),(.5,28.2),(.85,27.5),(1,27.8)], bumps=[(0,0,1.4,9)],
   zones=[(0,.3,'B',2.0)],
   trees=[(.2,-11,'oak',20,5.6),(.42,-14,'oak',19,5.2),(.1,10,'pine',24,2.4),(.62,10,'oak',19,5.0),(.93,-8,'oak',20,5.6),(.97,8,'oak',19,5.2)],
   bushes=[(.55,6,1.0),(.75,-7,1.1)],
   note='Fly-through: raised sand/straw pad, wide bare slope falling gently away to a basket framed by mossy oaks and pines.'),
 2: dict(fw=18, L=(10,24), R=(10,24), oak=.4, conf='medium',
   elev=[(0,28.5),(.3,27.5),(.55,26.5),(.8,25.0),(1,25.5)], bumps=[(.97,0,1.2,8)],
   zones=[(0,.25,'L',.7),(.45,.62,'B',3.0)],
   trees=[(.08,-9,'oak',21,6.0),(.2,-11,'oak',20,5.6),(.7,-6,'pine',25,2.4),(.74,7,'pine',24,2.4),(.9,-5,'pine',24,2.4)],
   bushes=[(.12,-5,1.2),(.5,-8,1.1),(.62,8,1.1),(.85,6,1.2)],
   note='Fly-through: gravel road with a wooden fence on the right, moss oaks over the left near the tee, open gravel clearing mid-hole, then pines close in around a basket on a duff bank.'),
 3: dict(fw=8, L=(6,22), R=(6,22), oak=.25, conf='medium',
   elev=[(0,21.5),(.25,20.5),(.5,19.5),(.7,19.5),(.9,20.8),(1,21.5)], bumps=[(.6,-9,2.0,8),(.95,5,.8,6)],
   zones=[(0,.3,'B',.8)],
   trees=[(.97,-4,'oak',20,5.8),(.5,5,'pine',27,2.4),(.75,-5,'pine',26,2.4)],
   bushes=[(.08,-4,1.5),(.1,4,1.5),(.18,-5,1.4),(.2,5,1.4),(.3,-4.5,1.3),(.33,4.5,1.3),(.55,-5,1.2),(.8,5,1.2)],
   note='Fly-through: narrow gravel trail between wiregrass and palmetto, tall pines, dips through a gully then climbs under a big moss oak at the basket.'),
 4: dict(fw=12, L=(12,22), R=(9,24), oak=.5, conf='medium',
   elev=[(0,22.0),(.3,21.5),(.55,21.0),(.8,20.0),(1,19.5)], bumps=[(0,0,1.5,8),(.5,-9,2.5,8)],
   zones=[(.4,.8,'R',99),(.35,.75,'L',.8)],
   trees=[(.45,-4,'oak',20,6.0),(.5,-9,'oak',21,6.2),(.7,-6,'oak',19,5.4),(.2,-10,'pine',24,2.4),(.9,5,'pine',25,2.4)],
   bushes=[(.2,-7,1.2),(.6,8,1.1),(.92,-6,1.0)],
   note='Fly-through: mulch tee with a rising bank on the left, big moss-draped oaks beside the dirt road mid-hole, fence and road open on the right, basket in dappled pines.'),
 5: dict(fw=10, L=(8,22), R=(8,22), oak=.25, conf='medium',
   elev=[(0,20.5),(.2,19.5),(.45,18.5),(.7,19.0),(.9,21.0),(1,22.0)], bumps=[(.22,-3,2.0,6),(1.0,0,2.5,10)],
   zones=[(.05,.3,'B',1.3),(.75,1,'R',.8)],
   trees=[(.12,-5.5,'pine',26,2.4),(.14,5.5,'pine',25,2.4),(.4,-5,'pine',25,2.4)],
   bushes=[(.08,-3.5,1.2),(.2,-4.8,1.1),(.55,5,1.3),(.8,5,1.5),(.9,6,1.6),(.95,-6,1.4)],
   note='Fly-through: turf tee with a bench, tall pines, an eroded sand bank to the left, narrow red-sand trail then up onto a bare sandy knoll with green brush behind the basket.'),
 6: dict(fw=18, L=(10,22), R=(10,22), oak=.1, conf='medium',
   elev=[(0,24.0),(.15,21.0),(.3,18.5),(.9,18.5),(1,19.5)], bumps=[(1.05,-7,4.0,9),(.75,-12,2.0,6)],
   zones=[(0,.25,'B',.9),(.35,.9,'B',99)],
   trees=[(.1,-5,'pine',22,2.3),(.12,6,'pine',22,2.3),(.2,-8,'oak',20,5.0),(.95,-9,'pine',22,2.3)],
   bushes=[(.4,-7,1.4),(.6,8,1.2),(.7,-6,1.4)],
   note='Fly-through: wooden tee platform over a wooded drop, steep bank down to a pale sandy pit with palmetto edges; basket on a rise at the far wall.'),
 7: dict(fw=14, L=(9,22), R=(7,22), oak=.5, conf='medium',
   elev=[(0,19.5),(.5,18.5),(1,18.0)], bumps=[(.7,-12,2.0,8)],
   zones=[(.0,.3,'L',2.0)],
   trees=[(.5,-10,'oak',21,6.0),(.75,-12,'oak',20,5.6),(.3,6,'pine',22,2.4),(.95,8,'oak',20,5.4)],
   bushes=[(.15,-6,1.4),(.2,-7,1.3),(.5,8,1.3),(.7,7,1.2),(.85,8,1.3)],
   note='Fly-through: low turf tee past wiregrass, open sandy lane, big moss oaks on the left bank, dense scrub down the right.'),
 8: dict(fw=14, L=(9,20), R=(9,22), oak=.35, conf='medium',
   elev=[(0,18.5),(.2,17.0),(.45,15.8),(.8,15.5),(1,16.5)], bumps=[(.95,-13,4.5,9),(.8,-18,3.0,8)],
   zones=[(.3,.8,'B',2.5)],
   trees=[(.45,2,'oak',21,6.5),(.6,8,'pine',23,2.4),(.1,10,'pine',24,2.4),(.9,-6,'pine',22,2.4)],
   bushes=[(.03,-5,1.6),(.05,4,1.6),(.12,-6,1.5),(.15,6,1.5),(.25,-6,1.3),(.3,6,1.2)],
   note='Fly-through: tee in tall wiregrass and palmetto, drop into a sandy bowl, one big moss oak mid-line, basket at the foot of a wooded bank.'),
 9: dict(fw=9, L=(6.5,22), R=(7,22), oak=.1, conf='medium',
   elev=[(0,17.5),(.2,16.0),(.5,12.5),(.8,11.5),(1,12.0)], bumps=[(.55,-4,3.0,5),(.6,-10,2.5,7),(.85,12,2.0,8)],
   trees=[(.3,-3,'pine',27,2.5),(.35,3,'pine',27,2.5),(.7,-5.5,'pine',25,2.4)],
   bushes=[(.04,-4,1.5),(.07,5,1.5),(.5,5,1.2),(.8,-5,1.4),(.85,5,1.3)],
   note='Fly-through: mulch tee in wiregrass, tall slender pines, steep drop past an eroded dirt cliff into a green bowl with the basket at the bottom.'),
 10: dict(fw=11, L=(6,22), R=(8,22), oak=.5, conf='medium',
   elev=[(0,13.5),(.4,13.0),(.75,11.8),(1,11.3)], bumps=[(1.0,5,1.0,8)],
   zones=[(0,.5,'L',.8)],
   trees=[(.12,-5,'oak',19,5.8),(.3,-6,'oak',18,5.5),(.55,4.5,'pine',24,2.4),(.8,-7,'oak',17,5.2)],
   bushes=[(.08,6,1.4),(.15,7,1.3),(.4,6.5,1.2)],
   note='Sandy trail along a low spine: moss-hung oaks crowd the left, pines and brush the right; gently downhill to a basket on a bank by a lawn.'),
 11: dict(fw=14, L=(8,22), R=(9,22), oak=.5, conf='medium',
   elev=[(0,13.0),(.15,12.0),(.3,11.0),(.5,11.5),(.7,13.5),(1,13.0)], bumps=[(0,0,1.2,7),(.55,0,1.2,7)],
   zones=[(.0,.35,'B',.8),(.4,.7,'R',2.5)],
   trees=[(.1,5.5,'oak',21,6.5),(.16,-4,'oak',20,6.5),(.24,6,'oak',19,6.0),(.3,-9,'oak',19,6.0),(.45,-6,'scrub',9,2.5),(.5,6,'scrub',10,2.6)],
   bushes=[(.05,-8,1.3),(.35,-7,1.2)],
   note='Tee on a built-up bank under huge spreading mossy oaks (one right on the line); drops through the oak meadow, closes into cedars and pines, then climbs to an elevated pine-duff clearing and basket.'),
 12: dict(fw=30, L=(10,22), R=(8,22), oak=.15, conf='medium',
   elev=[(0,14.0),(.2,12.8),(.5,12.0),(.8,12.0),(1,12.5)], bumps=[(1.1,0,3.0,10),(1.05,-10,2.0,8)],
   zones=[(.05,.7,'L',99),(.1,.7,'R',2.2),(.75,1,'B',1.4)],
   trees=[(.85,-6.5,'scrub',6,2.0)],
   bushes=[(.35,-8,1.2),(.9,5,1.0)],
   note='Deck tee above a wide open sandy lot (hay bales, flags); pines close the right; basket on a green patch at the foot of a grey limestone face.'),
 13: dict(fw=12, L=(6,22), R=(6,22), oak=.05, conf='medium',
   elev=[(0,13.5),(.3,12.7),(.7,12.0),(1,12.0)], bumps=[(1.12,0,6.5,9),(1.0,-7,1.4,5),(1.0,8,1.0,4),(.7,-4,.3,4)],
   zones=[(0,.15,'B',.8)],
   trees=[(.55,-3.5,'pine',26,2.5),(.35,3.5,'pine',25,2.5)],
   bushes=[(.2,5,1.0),(.6,6,1.2),(.9,-6,1.0)],
   note='Deck tee by a wooden rail; straight lane through tall pines on dirt and needles, flat then a touch downhill, ending on rock rubble at the base of a limestone quarry wall.'),
 14: dict(fw=34, L=(9,22), R=(9,22), oak=.25, conf='medium',
   elev=[(0,12.8),(.3,12.3),(.7,12.0),(1,12.0)], bumps=[],
   zones=[(.12,.88,'B',3.5)],
   trees=[(.25,10,'pine',25,2.6),(.45,-11,'pine',24,2.5),(.7,9,'pine',24,2.6),(.93,-6,'pine',24,2.6),(.95,7,'pine',23,2.5),(.97,-1,'scrub',7,2.2)],
   bushes=[(.4,-6,1.0),(.45,5,1.0),(.52,-4,0.9),(.96,3,1.3)],
   note='Long open pine-straw field: wide mown lane, sparse tall pines at the sides, hay bales and brush clumps, basket in front of a pine stand.'),
 15: dict(fw=14, L=(8,22), R=(8,22), oak=.4, conf='medium',
   elev=[(0,14.5),(.15,13.5),(.4,12.0),(.65,11.5),(.85,12.5),(1,14.5)], bumps=[(1.0,0,2.0,9)],
   zones=[(.0,.8,'B',.8),(.82,1,'B',3.5)],
   bushes=[(.82,-6,1.3),(.86,7,1.3),(.9,-5,1.3),(.93,6,1.3),(.97,-4,1.4),(.97,4,1.4),(.99,0,1.2)],
   note='Long sand-track corridor between tall pines and moss oaks that closes in; a long slow dip, then it opens onto wiregrass clumps and a basket on a sandy knoll.'),
 16: dict(fw=14, L=(8,22), R=(7,22), oak=.4, conf='medium',
   elev=[(0,14.5),(.2,13.0),(.5,11.5),(1,11.0)], bumps=[(.3,-4,1.4,4),(.4,5,1.3,4),(.5,-3,1.0,4),(.6,4,1.0,4)],
   zones=[(.0,.3,'R',.8)],
   trees=[(.12,5,'pine',24,2.4),(.9,-6,'oak',20,6.2),(.95,7,'scrub',6,2.2)],
   bushes=[(.4,-8,1.0)],
   note='Tee on a sandy pine ridge, falls over rolling dirt mounds, then the lane bends right to a basket under moss-draped oaks.'),
 17: dict(fw=14, L=(8,22), R=(8,22), oak=.5, conf='medium',
   elev=[(0,13.0),(.25,11.0),(.5,10.5),(.8,11.0),(1,11.5)], bumps=[(0,0,1.0,6),(.45,0,1.5,5)],
   zones=[(.6,1,'B',1.8)],
   trees=[(.06,-5,'oak',20,6.5),(.3,-7,'oak',19,6.0),(.55,4,'scrub',8,2.4),(.92,-9,'oak',18,5.2)],
   bushes=[(.1,6,1.2),(.45,-5,1.0),(.8,6,1.0)],
   note='Tee on a hump under a moss oak with a palm; drops into a wooded gully with a dirt berm across the lane (pond lies left), bends to a grassy clearing with brush behind the basket.'),
 18: dict(fw=26, L=(10,24), R=(9,24), oak=.55, conf='medium',
   elev=[(0,14.5),(.1,14.0),(.22,11.5),(.35,9.5),(.6,9.5),(.8,10.0),(1,10.0)], bumps=[(0,0,1.5,7)],
   zones=[(.0,.12,'B',.8),(.4,.6,'R',.8),(.55,1,'B',3.5)],
   trees=[(.07,-6,'oak',21,6.5),(.07,7,'oak',20,6.0),(.45,-10,'oak',20,6.0),(.8,-13,'oak',21,6.2),(.9,13,'oak',20,6.0)],
   bushes=[(.04,-8,1.5),(.05,9,1.5)],
   note='Tee on a high bluff of moss oaks and wiregrass, then a steep drop into the sandy quarry floor (OB-taped), a notch, then a flat mown finishing lawn ringed by banners.'),
}

# Heights in STYLE.elev share one chain along the round (each hole starts near where the last ended); OFFSET lowers the whole chain
# so the course falls about 10-15 m from hole 1 to the far end (an estimate: the real relief was not measured).
OFFSET = {1:-6,2:-6,3:-5.5,4:-5.5,5:-5,6:-5,7:-5,8:-5,9:-5,10:-4.5,11:-4.5,12:-4.5,13:-4.5,14:-4.5,15:-4.5,16:-4.5,17:-4.5,18:-4.5}
