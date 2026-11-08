# Hopeless Infinity - research table (milestone 1)

| Value | Used | Source |
|---|---|---|
| Sun - centre distance | 8.2 kpc | Bland-Hawthorn & Gerhard 2016, ARA&A 54, 529 |
| Thin disk scale length | 2.6 kpc | Bland-Hawthorn & Gerhard 2016 |
| Thin disk scale height | 300 pc | Juric et al. 2008, ApJ 673, 864 |
| Bar half-length / angle | 5.0 kpc / 27 deg | Bland-Hawthorn & Gerhard 2016 |
| Arm pitch angle | 13.1 deg | Vallee 2015, MNRAS 450, 4277 |
| Arms | 4 | Vallee four-arm model |
| Star class fractions O..M | 0.00003, 0.13, 0.6, 3, 7.6, 12.1, 76.5 % | LeDrew 2001, JRASC 95, 32 (via Las Cumbres Obs.) |
| Star Teff, L, R, M | dwarf sequence rows | Mamajek v2022.04.16 / Pecaut & Mamajek 2013 |
| Mass inside a class | IMF slopes 1.3 / 2.3, break 0.5 Msun | Kroupa 2001, MNRAS 322, 231 |
| Habitable zone | Table 3 coefficients | Kopparapu et al. 2013, ApJ 765, 131 |
| Mass - radius | 1.008 M^0.279, 0.589, -0.044, 0.881 | Chen & Kipping 2017, ApJ 834, 17 |
| Snow line | 2.7 AU x sqrt(L) | Hayashi 1981 |
| Planet spacing | >= 10, mode 20 mutual Hill radii | Weiss et al. 2018, AJ 155, 48 |
| Outer neighbour larger | 65 % | Weiss et al. 2018 |
| Planets per star | >= 1.6 | Cassan et al. 2012, Nature 481, 167 |
| Surface gravity | g = 9.80665 M / R^2 | standard gravity (NIST) |
| Equilibrium temperature | 278.6 K S^0.25 (1-A)^0.25, A = 0.306 | Earth Bond albedo, NASA fact sheet |

GAME values (our tuning, not physics): arm contrast and sharpness, bulge and bar strength, disk edge 15 kpc, 100 pc sectors,
system count, inner orbit 0.03-0.3 AU, planet mass draws, 1024 blocks per Earth radius, start planet 0.6-1.5 Earth masses.

Known gaps: G stars with a temperate rocky planet 16 % (Bryson et al. 2021 give higher); gas giants are rare;
Chen-Kipping median makes Jupiter 13.8 Re (real 11.2). Main-sequence stars only (no giants, white dwarfs, binaries yet).

## Milestone 2 - the start planet (0.1.0)

| Value | Used | Source |
|---|---|---|
| Earth elevation spread | ETOPO5 500 m bins, 29.7 % land | NOAA NGDC ETOPO5 (1988), tabled by S. Dutch |
| Mean temperature by latitude | T0 + T2 P2(sin lat), T2 = -28.0 C | North 1975, J. Atmos. Sci. 32, 2033 |
| Greenhouse warming | 33.1 K (Earth 288.1 K - 255 K) | standard Earth energy balance |
| Lapse rate | 6.5 K per km | ICAO standard atmosphere |
| Cube-to-sphere map | Nowell's mapping | P. Nowell 2005, "Mapping a cube to a sphere" |
| Fall damage | x g/9.80665 (impact energy m g h) | physics |

GAME: 40 m of height per block (the planet is ~5700x smaller than Earth across; 20 m gave slopes 284x too steep),
512-block void gaps between faces, zonal wetness curve (shape of GPCP zonal-mean precipitation), biome bands.
Measured (tools/PlanetTest): land 28.4 %, seams within the local slope at all 6048 edge samples, folds return home 6048/6048,
equator - pole 41.1 K (North: 42 K), land steeper than 2 blocks/block 3.8 %, column plans 0.3 ms per chunk.

## Galaxy v2 - real size and real counts (2026-11-02)

Measured by tools/GalaxyTest (docs/test-galaxy-v2.txt): 31 of 31 pass.

| Check | Generator | Published |
|---|---|---|
| Objects within 20 pc | 3,524 | ~3,600 (Kirkpatrick 2024) |
| White dwarf density at the Sun | 5.8e-3 /pc^3 | 4.49e-3 (Hollands 2018) |
| Class shares A/F/G/K/M | 0.5/2.6/3.4/12.1/81.2 % | 1.1/2.3/5.1/10.8/80.7 % (Reyle 2021, within 2 sigma) |
| Star : brown dwarf | 3.1 : 1 | 4 : 1 (Kirkpatrick 2024) |
| Multiplicity BD/M/FGK/A/B/O | 22/26/43/50/59/83 % | 22/26/44/50/60/80 % (Duchene & Kraus 2013) |
| Objects in the galaxy | 1.33e11 | 1.22e11 (5e10 Msun / 0.41) |
| Neutron stars / black holes | 6.2e8 / 2.6e8 | 4.4e8 / 2.2e8 (Lam 2020) |
| Supernovae per century | 1.77 | 1.63 +/- 0.46 (Rozwadowska 2021) |
| Wolf-Rayet stars | 618 | 1200 +300/-100 (Rosslowe & Crowther 2015) |
| Hot Jupiters (FGK) | 1.2 % | 1.2 % (Wright 2012) |
| 0.3-10 MJ at 2-2000 d (FGK) | 9.1 % | 10.5 % (Cumming 2008) |
| Cold debris belts (FGK) | 16.8 % | 17.1 % (Sibthorpe 2018) |
| Moon-like moons, rocky > 0.5 Me | 1 in 12 | 1 in 12 (Elser 2011) |
| Regular moon mass / giant mass | 1.0e-4 | 1e-4 (Canup & Ward 2006) |
| Main belt largest body | 938 km median | Ceres 939 km |
| Rogue planets per star | 21.1 | 21 (Sumi 2023) |
| Sgr A* | 4.154e6 Msun, horizon 1.227e7 km | GRAVITY 2019 |

Sources added: IMF Kirkpatrick et al. 2024; present SFR 1.65 Msun/yr (Licquia & Newman 2015); IFMR Kalirai 2008; NS radius 12.4 km (Miller 2021);
binaries Raghavan 2010 (log P 5.03 +/- 2.28); S-type limit Holman & Wiegert 1999; giant occurrence ~ M* (Johnson 2010); disk mass 0.2-0.6 % (Andrews 2013);
belts Pitjeva & Pitjev 2018, Dohnanyi 1969; Oort cloud 1.5e12 (Boe 2019); Saturn ring edges (NASA fact sheet); R0 8.178 kpc (GRAVITY 2019).
GAME: old-disk arm strength 0.15, young stars 50 pc scale height, 3 active comets, 25 irregular moons for a Jupiter-size Hill sphere, giant radii/temperatures.
