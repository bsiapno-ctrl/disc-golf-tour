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
 1: dict(fw=16, L=(10,22), R=(10,22), oak=.55, conf='medium',
   elev=[(0,30.0),(.5,29.0),(1,28.0)], bumps=[(0,0,1.2,9)],
   trees=[(.82,-11,'oak',20,5.4),(.9,9,'oak',19,5.0),(.35,-15,'oak',18,4.8)],
   bushes=[(.55,7,1.0)], zones=[(0,.25,'B',2.5)],
   note='Short, open sand-and-straw lane from a raised turf pad; big mossy live oaks flank the basket.'),
 2: dict(fw=22, L=(11,24), R=(9,24), oak=.35, conf='medium',
   elev=[(0,28.5),(.25,26.0),(.5,23.5),(.75,21.5),(1,21.0)], bumps=[(.9,0,-.8,10)],
   zones=[(.03,.55,'B',99),(.03,.55,'R',3.0)],
   trees=[(.74,-6.5,'pine',25,2.4),(.76,6.5,'pine',24,2.4),(.5,-17,'oak',18,5.0)],
   bushes=[(.62,-8,1.1),(.7,9,1.0)],
   note='Long downhill hole: tee beside a wooden fence, wide mown lawn that drops away, then a pine-lined chute to a basket low in the trees.'),
 3: dict(fw=9, L=(6.5,22), R=(6.5,22), oak=.4, conf='medium',
   elev=[(0,21.5),(.4,19.5),(.75,19.0),(1,20.5)], bumps=[],
   trees=[(.55,6,'oak',19,5.0)],
   bushes=[(.2,-5,1.0),(.45,5.5,1.0)],
   note='Tight lane through mixed pine and live-oak woods on a sandy trail; the line bends right near the end.'),
 4: dict(fw=14, L=(13,22), R=(8,24), oak=.45, conf='medium',
   elev=[(0,22.0),(.35,21.0),(.7,20.0),(1,20.5)], bumps=[(0,0,1.5,8)],
   zones=[(.0,.7,'L',2.2)],
   trees=[(.38,-9,'oak',19,5.2),(.8,8,'pine',24,2.4)],
   bushes=[(.2,-7,1.2),(.6,8,1.1),(.92,-6,1.0)],
   note='Tee on a built-up pad; fence and open ground on the left, oaks and pines closing in on the right. Basket in pine duff.'),
 5: dict(fw=14, L=(8,22), R=(8,22), oak=.2, conf='medium',
   elev=[(0,20.5),(.3,19.0),(.6,18.5),(.85,19.5),(1,22.5)], bumps=[(1.0,0,2.5,10)],
   zones=[(.05,.25,'B',1.4),(.4,.8,'B',3.0)],
   trees=[(.15,-5.5,'pine',26,2.4),(.15,5.5,'pine',25,2.4)],
   bushes=[(.08,-3.5,1.2),(.14,3.8,1.2),(.2,-4.8,1.1),(.22,5.0,1.1),(.55,9,1.3),(.58,-10,1.2)],
   note='Starts through tall pines and wiregrass, opens to a sandy clearing, then climbs to a basket on a raised sandy knoll.'),
 6: dict(fw=22, L=(10,22), R=(10,22), oak=.1, conf='medium',
   elev=[(0,24.0),(.12,21.0),(.3,18.5),(.9,18.5),(1,19.3)], bumps=[(1.05,-7,4.0,9)],
   zones=[(.0,1,'B',2.2)],
   trees=[(.9,-9,'pine',22,2.3),(.55,15,'pine',21,2.3)],
   bushes=[(.6,6,1.0),(.8,-6,1.0)],
   note='Elevated wooden tee box above a sandy pit floor (a taped line crosses the sand); basket on a slight rise at the far side.'),
 7: dict(fw=15, L=(9,22), R=(9,22), oak=.5, conf='medium',
   elev=[(0,19.5),(.5,18.0),(1,17.0)], bumps=[],
   zones=[(.0,.5,'B',1.8)],
   trees=[(.72,6.5,'oak',20,5.4),(.95,9,'oak',20,5.4),(.3,-12,'oak',18,4.8)],
   bushes=[(.2,-6,1.2),(.25,-7,1.1)],
   note='Open sandy tee; one big live oak crowds the right side of the line and shades the basket.'),
 8: dict(fw=14, L=(9,20), R=(9,22), oak=.35, conf='medium',
   elev=[(0,18.0),(.5,16.5),(1,17.0)], bumps=[(.95,-13,4.5,9),(.8,-18,3.0,8)],
   trees=[(.45,8,'pine',22,2.4)],
   bushes=[(.25,-6,1.3),(.3,6,1.2),(.6,-7,1.1)],
   note='Gravel path from the tee; basket tucked under a bank with a wooden staircase on the left.'),
 9: dict(fw=9, L=(7.5,22), R=(7.5,22), oak=.15, conf='medium',
   elev=[(0,17.5),(.2,15.0),(.6,12.5),(1,12.5)], bumps=[(.85,-12,2.5,9),(.85,12,2.5,9)],
   trees=[(.5,6,'pine',26,2.5),(.7,-5.5,'pine',25,2.4)],
   bushes=[(.8,-5,1.4),(.85,5,1.3)],
   note='Downhill from a turf tee into a wooded gully; tall pines and wiregrass around a basket in the low ground.'),
 10: dict(fw=11, L=(7,22), R=(7,22), oak=.3, conf='low-medium',
   elev=[(0,13.0),(.5,12.0),(1,12.5)], bumps=[],
   note='Straight trail corridor through pine and oak woods; wooded both sides.'),
 11: dict(fw=14, L=(8,22), R=(9,22), oak=.5, conf='low-medium',
   elev=[(0,12.5),(.4,11.5),(.7,12.0),(1,13.0)], bumps=[],
   zones=[(.4,.8,'R',2.5)],
   trees=[(.55,-14,'oak',20,5.6),(.65,12,'oak',19,5.2)],
   note='Two-bend par 4 through woods into an oak meadow; big mossy oaks beside the middle of the hole.'),
 12: dict(fw=18, L=(10,22), R=(10,22), oak=.3, conf='low-medium',
   elev=[(0,12.0),(.5,13.0),(1,13.5)], bumps=[],
   zones=[(.1,.9,'B',2.0)],
   note='Long par 3 down a clearing lined with pines; basket in open grass.'),
 13: dict(fw=14, L=(10,22), R=(10,22), oak=.2, conf='low-medium',
   elev=[(0,14.0),(.6,12.0),(1,11.0)], bumps=[(1.12,0,7.5,10),(1.1,-12,5.0,9),(1.1,12,5.0,9)],
   zones=[(0,1,'B',1.6)],
   bushes=[(.9,-8,1.0),(.9,8,1.0)],
   note='Basket at the foot of a limestone wall in an old quarry bowl; gravel floor, pines on the rim.'),
 14: dict(fw=14, L=(9,22), R=(9,22), oak=.35, conf='low',
   elev=[(0,12.0),(.35,13.0),(.7,13.0),(1,12.0)], bumps=[],
   note='Long two-bend par 5 through woods (UDisc geometry only; not studied on video).'),
 15: dict(fw=22, L=(11,24), R=(11,24), oak=.3, conf='low-medium',
   elev=[(0,12.0),(.5,12.5),(1,13.5)], bumps=[],
   zones=[(.1,.9,'B',2.2)],
   bushes=[(.96,-5,1.2),(.97,5,1.2)],
   note='Longest hole: a straight par 5 over rolling open ground between tree lines; palmettos and wiregrass near the basket.'),
 16: dict(fw=12, L=(8,22), R=(8,22), oak=.3, conf='low',
   elev=[(0,13.0),(.5,12.0),(1,11.5)], bumps=[],
   note='Par 4 that bends twice through woods (UDisc geometry only).'),
 17: dict(fw=16, L=(10,22), R=(10,22), oak=.45, conf='low-medium',
   elev=[(0,11.5),(.5,10.5),(1,12.0)], bumps=[(.97,0,1.5,7)],
   zones=[(.6,1,'B',2.0)],
   trees=[(.9,-9,'oak',19,5.2)],
   note='Bending par 4 ending on a lawn; stone steps and a low ledge by the basket.'),
 18: dict(fw=24, L=(12,24), R=(11,24), oak=.55, conf='low-medium',
   elev=[(0,13.5),(.3,11.0),(1,10.0)], bumps=[(0,0,2.0,7)],
   zones=[(.05,1,'B',3.0)],
   trees=[(.5,-14,'oak',21,6.0),(.8,15,'oak',19,5.4)],
   note='Finishing hole: raised wooden tee platform, open lawn with scattered big oaks, basket near the crowd area.'),
}

# Heights in STYLE.elev share one chain along the round (each hole starts near where the last ended); OFFSET lowers the whole chain
# so the course falls about 10-15 m from hole 1 to the far end (an estimate: the real relief was not measured).
OFFSET = {1:-6,2:-6,3:-5.5,4:-5.5,5:-5,6:-5,7:-5,8:-5,9:-5,10:-4.5,11:-4.5,12:-4.5,13:-4.5,14:-4.5,15:-4.5,16:-4.5,17:-4.5,18:-4.5}
