import math
Phi=lambda z:0.5*(1+math.erf(z/math.sqrt(2)))
ART={'common':0.1,'holo':0.45,'double_rare':0.35,'hyper_rare':0.55,'ultra_rare':0.75,'illustration_rare':0.85,'special_illustration_rare':1.0}
TEX={'common':0.1,'holo':0,'double_rare':0,'hyper_rare':-0.2,'ultra_rare':-0.1,'illustration_rare':-0.05,'special_illustration_rare':-0.15}
def era(y):
    # (mu, sigma, anchor grade, phi base)
    if y<=2003: return 7.5,1.15,6.6,0.36
    if y<=2015: return 8.3,0.95,7.6,0.30
    if y<=2020: return 9.0,0.80,8.3,0.30
    return 9.25,0.72,8.7,0.35
def shares(mu,sd):
    s={}
    for g in range(1,11):
        hi=1 if g==10 else Phi((g+0.5-mu)/sd); lo=0 if g==1 else Phi((g-0.5-mu)/sd)
        s[g]=max(1e-4,hi-lo)
    return s
def value(r,y,rar,P,pack):
    mu,sd,anc,phi=era(y); mu+=TEX[rar]
    s=shares(mu,sd)
    A=ART[rar]
    U=min(2.5,max(0,math.log(max(pack,1)/5)))          # set scarcity (expensive pack = little product)
    D=math.exp(0.6*(P-0.5)+0.3*(A-0.5)+0.1*U)            # collector demand for perfect copies
    comp=(1+r/100)**(-0.15*min(1,s[10]/0.25))                                  # expensive cards: more sent to PSA -> smaller premium
    lam={8:1.12,9:1.25,10:1.45}; eps={8:0.6,9:0.6,10:0.85}; dl={8:0.2,9:0.4,10:0.7}
    rel={7:1.0}
    for g in (8,9,10):
        ratio=lam[g]*D**dl[g]*max(1,s[g-1]/s[g])**eps[g]
        rel[g]=rel[g-1]*ratio**comp
    ph=min(0.6,phi*D**0.5)
    for g in range(1,7): rel[g]=ph+(1-ph)*((g-1)/6)**1.5
    # anchor: the ungraded price equals grade 'anc' (logarithmic interpolation)
    lo=int(anc); f=anc-lo
    a=math.exp(math.log(rel[lo])*(1-f)+math.log(rel[lo+1])*f)
    base=r/a
    w={10:1,9:0.4,8:0.22}
    out={}
    s10=s[10]
    prev=0
    for g in range(1,11):
        prem=16*D*(0.35/s10)**0.5*(w[g] if g in w else 0.03+0.07*(g-1)/6)
        v=base*rel[g]+prem
        v=min(v, r*120+40)              # anti-absurdity cap
        v=max(v,prev); prev=v; out[g]=v
    return out,s[10],D
cards=[("Charizard Base Set (1999, unlimited holo)",450,1999,'holo',1.0,500),
("Dark Charizard Team Rocket (2000, holo)",150,2000,'holo',1.0,300),
("Rattata Base Set (1999, common)",0.5,1999,'common',0.25,500),
("Umbreon VMAX alt 'Moonbreon' EVS (2021)",1400,2021,'ultra_rare',1.0,12),
("Umbreon ex SIR Prismatic (2025)",1000,2025,'special_illustration_rare',1.0,12),
("Charizard ex SIR 151 (2023)",110,2023,'special_illustration_rare',1.0,8),
("Charizard GX Shiny Hidden Fates (2019)",100,2019,'hyper_rare',1.0,15),
("Pikachu IR modern (2024)",5,2024,'illustration_rare',0.95,5),
("Unpopular modern common (2024)",0.10,2024,'common',0.1,4.5),
("Unpopular gold ultra rare (2023)",3,2023,'hyper_rare',0.2,4.5),
("Giratina VSTAR GG Crown Zenith (2023)",90,2023,'ultra_rare',0.75,7),
("Mid-tier Pokemon SIR (2024)",25,2024,'special_illustration_rare',0.5,5)]
for n,r,y,rar,P,pack in cards:
    v,g10,D=value(r,y,rar,P,pack)
    print(f"{n}: raw ${r} | gem {g10*100:.0f}% D={D:.2f} | "+" ".join(f"{g}:{v[g]:,.0f}" if v[g]>=10 else f"{g}:{v[g]:.2f}" for g in range(1,11))+f" | x10={v[10]/r:.1f}")
