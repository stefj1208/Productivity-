# Notre Semaine

Application Android pour deux personnes : planifier la semaine, préparer le lendemain en 2 minutes, suivre UNE priorité par jour et par semaine, et se soutenir mutuellement — sans surcharge, sans gamification.

**Version actuelle : V7.** Cinq destinations, une question chacune : *que fait-on maintenant* (Aujourd'hui, qui contient aussi la semaine), *où va-t-on* (Objectifs), *qu'est-ce qu'on mange* (Maison), *où en est-on à deux* (Nous), *qu'est-ce qui me concerne* (Moi). **La roue dentée a disparu** : tout ce qui était enterré dans les réglages a sa tuile dans « Moi », et chaque tuile affiche son état. Les rappels s'affichent en **alarme plein écran**, même téléphone verrouillé.

Le numéro de version est affiché dans **Moi → Profil & apparence** : c'est le moyen de vérifier ce qui est réellement installé.

---

## 1. Comment obtenir l'application (aucune installation d'outil nécessaire)

À chaque mise à jour du code, GitHub fabrique automatiquement le fichier d'installation (APK). Pour le récupérer :

1. Sur GitHub, ouvrez ce projet puis l'onglet **Actions** (en haut).
   - **Si cet onglet est vide** : GitHub Actions est probablement désactivé sur le dépôt. Allez dans **Settings → Actions → General**, choisissez **Allow all actions and reusable workflows**, enregistrez. S'il affiche un bouton du type « I understand my workflows, go ahead and enable them », cliquez-le. La prochaine mise à jour du code déclenchera la fabrication de l'APK (ou ouvrez le workflow « Construire l'APK » et cliquez **Run workflow**).
2. Cliquez sur la dernière exécution **« Construire l'APK »** avec une coche verte ✓.
3. Tout en bas de la page, section **Artifacts** : cliquez sur **NotreSemaine-APK** pour télécharger un fichier ZIP.
4. Envoyez ce ZIP sur chaque téléphone (par e-mail, câble, ou Google Drive), ouvrez-le : il contient `app-debug.apk`.

### Installer sur le Samsung Galaxy S23 et le Honor 400 Pro

1. Sur le téléphone, touchez le fichier `app-debug.apk`.
2. Android affiche « Installation d'applications inconnues » : autorisez **pour cette source uniquement** (par exemple l'appli Fichiers). C'est normal : l'application ne vient pas du Play Store.
3. Touchez **Installer**. L'icône « Notre Semaine » apparaît.
4. Au premier lancement : chacun saisit son prénom et choisit sa couleur (bleu ou orange — prenez chacun une couleur différente).

> L'application fonctionne immédiatement, entièrement hors ligne. La synchronisation entre vos deux téléphones est **facultative** et s'active plus tard dans **Moi → Synchronisation** (voir `SETUP-SUPABASE.md`).

---

## 2. Ce que fait la V1

| Écran | Rôle |
|---|---|
| **Planning** | **La boussole** en haut : une carte qui dit quoi faire maintenant. Puis le **récap du jour** (priorité, tâches, « 2/3 fait »), le **récap de la semaine** (priorité, bande des 7 jours avec l'avancement de chacun), les **raccourcis** (Menus, Courses, Rituel, Objectifs), et deux boutons : **Planifier la semaine** et Préparer demain. |
| **Objectifs** | La bibliothèque « clé en main » : choisissez « Apprendre une langue » ou « (Re)prendre le sport », répondez à 3 questions, l'app place les séances de la semaine à votre place et suit la progression. |
| **Maison** | Ce qu'on mange aujourd'hui (matin, midi, soir), l'accès aux **menus de la semaine** et à la **liste de courses** rangée par rayon. |
| **Nous** | Vos deux semaines côte à côte, le **Pacte d'écran des deux** visible en permanence, les demandes de pause à accorder, l'accès direct à votre pacte et à Sommeil & sport, et le bouton « bravo 👏 ». |
| **Moi** | Tout ce qui me concerne, en tuiles qui affichent leur état : rituel du matin (série en cours), pacte d'écran (limite et minutes du jour), sommeil & sport (moyenne de la semaine), assistant (fournisseur détecté), rappels, synchronisation, la méthode, profil & apparence. Chaque tuile ouvre un écran dédié. |

Ce que l'application applique des 6 livres (détail dans l'écran « La méthode ») :
- **Une seule priorité** par jour et par semaine (One Thing) ; **3 tâches par jour maximum** et **3 objectifs actifs maximum** (Essentialisme) — l'app refuse le surplus, volontairement.
- **Rituel S.A.V.E.R.S.** avec minuteur enchaîné, série de jours et bouton qui règle le réveil (Miracle Morning).
- **Capture partout en 3 secondes** avec le bouton « + », tri de la boîte de réception chaque dimanche (GTD). « Rappeler le plombier mardi » devient tout seul une tâche datée mardi — sans IA, par analyse du texte.
- **Blocs de concentration** décidés la veille, séances d'objectifs protégées (Deep Work).
- **Pacte d'écran** : limite quotidienne sur les applis choisies, déblocage uniquement par le partenaire (élimination des distractions, Semaine de 4 heures).
- **Aucune culpabilisation** : gris neutre pour un objectif manqué, aucun badge.
- **Rappels au moment d'agir** : rituel du matin, séance d'objectif, préparer demain, revue du dimanche, et 15 minutes avant le couvre-feu. En alarme plein écran (son + vibration, par-dessus l'écran verrouillé) ou en simples notifications — au choix, dans **Moi → Rappels**.

## 3. Architecture en langage simple

- **L'application** est écrite en Kotlin (le langage officiel d'Android) avec Jetpack Compose pour l'interface. Elle tourne à 100 % sur le téléphone.
- **Les données** (tâches, priorités, plans) sont stockées **d'abord sur le téléphone** dans une petite base de données. C'est pour ça que tout marche sans réseau.
- **La synchronisation** (facultative) passe par **Supabase**, un service hébergé en Europe : chacun a son compte, vous reliez les deux comptes avec un code à 6 caractères, et seuls vous deux pouvez lire vos données (règles de sécurité vérifiées par le serveur, pas seulement par l'application).
- **Les rappels** sont posés par l'horloge du téléphone (alarmes exactes) — rien ne part sur internet pour vous les envoyer.

## 4. Permissions à accorder (une fois, guidées dans l'app)

Pour le temps d'écran et le Pacte, Android exige une permission spéciale hors de l'application :
**Moi → Mon pacte d'écran** → suivre les étapes affichées (l'app ouvre le bon écran Android toute seule).

**Important sur le Honor 400 Pro (MagicOS)** : Paramètres → Batterie → Lancement d'applications → Notre Semaine → désactiver « Gestion automatique » et tout autoriser en manuel. Sans cela, MagicOS tue la surveillance en arrière-plan. Sur le S23 : Paramètres → Batterie → « Non restreinte ».

Réaliste, pour être honnête :
- Le blocage repose sur la détection de l'appli au premier plan : il s'affiche en général en 2 à 5 secondes. Ce n'est pas un verrou inviolable (désinstaller l'app le contourne) — c'est un **pacte**, tenu à deux.
- La demande de pause part instantanément ; le partenaire la voit à l'ouverture de son application (pas de notification poussée en V2).
- **IA locale : abandonnée d'un commun accord.** L'analyse de texte intégrée (dates et mots-clés français) couvre la capture sans réseau.

## 4 bis. L'assistant (facultatif)

Tout ce que l'assistant fait a un équivalent **hors ligne, instantané et gratuit** : banque de menus, premiers pas par objectif, répartition des séances, classement des courses par rayon. L'assistant sert au sur-mesure et aux moments de panne d'inspiration.

Il se branche dans **Moi → Assistant** avec **votre propre clé** : une clé Google (aistudio.google.com) ou une clé Anthropic (`sk-ant-…`). L'application reconnaît laquelle toute seule. Désactivé par défaut ; rien ne part du téléphone tant qu'il ne l'est pas.

Où il intervient :

| Écran | Ce qu'il fait | Ce qui sort du téléphone |
|---|---|---|
| Objectifs | Bâtit le rythme : séances, durée, moment, jours, première action | L'intitulé de l'objectif |
| Objectifs | Trois premiers pas concrets | L'intitulé de l'objectif |
| Revue du dimanche | Propose LA priorité et ce qu'on laisse tomber | Objectifs non privés, notes en attente |
| Préparer demain | Choisit la priorité du jour et deux tâches | Priorité de la semaine, objectifs non privés, tâches en attente |
| Capture « + » | Transforme la note en action et choisit le jour | La note seule |
| Menus | Une semaine sur mesure | Vos contraintes de repas |
| Courses | Range les articles restés dans « Divers » | Ces articles seuls |
| Temps d'écran | Propose un pacte tenable d'après l'usage mesuré | Vos moyennes d'écran et l'heure de lever |
| Santé | Une phrase sur la semaine, un levier à essayer | Vos moyennes de la semaine |

Ce qui ne sort **jamais** : un objectif marqué privé, quoi que ce soit du partenaire, le détail jour par jour de la santé, les identifiants de synchronisation.

Chaque proposition **remplit les champs** — rien n'est enregistré tant que vous n'avez pas validé. Si la clé est refusée, le quota atteint ou le réseau absent, l'application le dit en une phrase et la voie hors ligne reste disponible.

## 5. Feuille de route

1. ✅ **V1** — semaine, veille, priorité du jour, espace partagé
2. ✅ **V2** — objectifs clé en main, rituel du matin + réveil, capture GTD + analyse du français, conseils des 6 livres, temps d'écran + Pacte
3. ✅ **V3** — navigation entre semaines, boussole, conseils visuels, Health Connect + saisies de secours, menus + liste de courses, graphiques 4 semaines
4. ✅ **V4** — Pacte visible en permanence des deux côtés, couvre-feu, petit-déjeuner dans les menus
5. ✅ **V5** — assistant facultatif (Google ou Anthropic) branché sur neuf écrans, chaque fois doublé d'une voie hors ligne
6. ✅ **V6** — refonte de la navigation (4 onglets, plus de séparation jour/semaine), Maison en onglet, rappels en alarme plein écran
7. ✅ **V7** — refonte UX : onglet « Moi » à la place de la roue dentée, réglages éclatés en écrans dédiés, en-têtes et états vides cohérents partout
8. ✅ **V7.1** — passage « Don't Make Me Think » : retour en haut à gauche sur tous les écrans, moitié des mots supprimée, champs de saisie qui vont à la ligne
9. ✅ **V8** — « Aujourd'hui » devient **Planning** (récap jour + semaine + raccourcis + bouton Planifier la semaine) ; **modifier et supprimer** ajoutés sur les tâches, les objectifs, les repas et les courses
10. ✅ **V8.1** — **confier une tâche à l'autre**, écran **Mes performances** (KPI + graphiques), grille de 8 raccourcis en symboles, « Préparer demain » accessible à tout moment, adresse Supabase corrigeable et nettoyée automatiquement

### Sommeil & sport : ce qui marchera vraiment
Health Connect ne contient **que** ce qu'une montre, un bracelet ou une appli (Samsung Health…) y écrit. Sans source, sommeil et séances resteront à zéro — c'est une limite d'Android, pas de l'application. D'où la saisie de secours dans **Réglages → Sommeil & sport** : deux heures à taper pour la nuit, un bouton `20′ / 30′ / 45′ / 60′` pour une séance. Une mesure automatique n'écrase jamais une saisie manuelle du même jour.

## 6. Pour les curieux : compiler soi-même

Ouvrir le dossier dans Android Studio (dernière version stable) et lancer « Run ». Rien d'autre à configurer.
