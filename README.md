# Notre Semaine

Application Android pour deux personnes : planifier la semaine, préparer le lendemain en 2 minutes, suivre UNE priorité par jour et par semaine, et se soutenir mutuellement — sans surcharge, sans gamification.

**Version actuelle : V3** — la V2, plus : navigation libre entre les semaines (planifier la suivante n'importe quand, revoir les précédentes), une **boussole** qui affiche à tout instant la seule chose à faire maintenant, des conseils en cartes visuelles, **Health Connect** (sommeil, pas, séances) avec saisie de secours en 10 secondes, **menus de la semaine + liste de courses** générée et rangée par rayon, et des **graphiques sur 4 semaines** (sommeil, sport, réseaux).

Rappel de la V2 : objectifs « clé en main », rituel du matin S.A.V.E.R.S. avec réveil, capture rapide en français, et le **Pacte d'écran** que seul le partenaire peut lever.

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

> L'application fonctionne immédiatement, entièrement hors ligne. La synchronisation entre vos deux téléphones est **facultative** et s'active plus tard dans Réglages (voir `SETUP-SUPABASE.md`).

---

## 2. Ce que fait la V1

| Écran | Rôle |
|---|---|
| **Aujourd'hui** | **La boussole** : une seule carte, en très gros, qui dit quoi faire maintenant (rituel, priorité, planifier la semaine, vider la boîte…). Puis le détail du jour et un conseil en carte. |
| **Semaine** | Flèches ‹ › pour aller **d'une semaine à l'autre**, passé comme futur — vous pouvez planifier la semaine prochaine dès le lundi. Les 7 jours, la répartition en un tap, l'accès aux menus et aux courses, et les **graphiques sur 4 semaines**. Bouton « Planifier cette semaine » (8 étapes guidées). |
| **Objectifs** | La bibliothèque « clé en main » : choisissez « Apprendre une langue » ou « (Re)prendre le sport », répondez à 3 questions, l'app place les séances de la semaine à votre place et suit la progression. |
| **Nous** | Vos deux semaines côte à côte, bouton « bravo 👏 », temps d'écran de chacun, et les demandes de pause du Pacte à accorder ou non. |
| **Réglages** | (roue dentée en haut de l'écran Aujourd'hui) Profil, rappels, thème, synchronisation, Temps d'écran & Pacte, **Sommeil & sport**, « La méthode ». |
| **Menus & Courses** | (depuis Semaine) Un plat et ses ingrédients par repas ; la liste de courses est générée, **les quantités additionnées** et **rangée par rayon**, cochable en un tap. |

Ce que l'application applique des 6 livres (détail dans l'écran « La méthode ») :
- **Une seule priorité** par jour et par semaine (One Thing) ; **3 tâches par jour maximum** et **3 objectifs actifs maximum** (Essentialisme) — l'app refuse le surplus, volontairement.
- **Rituel S.A.V.E.R.S.** avec minuteur enchaîné, série de jours et bouton qui règle le réveil (Miracle Morning).
- **Capture partout en 3 secondes** avec le bouton « + », tri de la boîte de réception chaque dimanche (GTD). « Rappeler le plombier mardi » devient tout seul une tâche datée mardi — sans IA, par analyse du texte.
- **Blocs de concentration** décidés la veille, séances d'objectifs protégées (Deep Work).
- **Pacte d'écran** : limite quotidienne sur les applis choisies, déblocage uniquement par le partenaire (élimination des distractions, Semaine de 4 heures).
- **Aucune culpabilisation** : gris neutre pour un objectif manqué, aucun badge, 2 rappels/jour maximum.

## 3. Architecture en langage simple

- **L'application** est écrite en Kotlin (le langage officiel d'Android) avec Jetpack Compose pour l'interface. Elle tourne à 100 % sur le téléphone.
- **Les données** (tâches, priorités, plans) sont stockées **d'abord sur le téléphone** dans une petite base de données. C'est pour ça que tout marche sans réseau.
- **La synchronisation** (facultative) passe par **Supabase**, un service hébergé en Europe : chacun a son compte, vous reliez les deux comptes avec un code à 6 caractères, et seuls vous deux pouvez lire vos données (règles de sécurité vérifiées par le serveur, pas seulement par l'application).
- **Les rappels** sont des notifications locales — rien ne part sur internet pour vous les envoyer.

## 4. Permissions à accorder (une fois, guidées dans l'app)

Pour le temps d'écran et le Pacte, Android exige une permission spéciale hors de l'application :
Réglages (roue dentée) → **Temps d'écran & Pacte** → suivre les 4 étapes affichées (l'app ouvre le bon écran Android toute seule).

**Important sur le Honor 400 Pro (MagicOS)** : Paramètres → Batterie → Lancement d'applications → Notre Semaine → désactiver « Gestion automatique » et tout autoriser en manuel. Sans cela, MagicOS tue la surveillance en arrière-plan. Sur le S23 : Paramètres → Batterie → « Non restreinte ».

Réaliste, pour être honnête :
- Le blocage repose sur la détection de l'appli au premier plan : il s'affiche en général en 2 à 5 secondes. Ce n'est pas un verrou inviolable (désinstaller l'app le contourne) — c'est un **pacte**, tenu à deux.
- La demande de pause part instantanément ; le partenaire la voit à l'ouverture de son application (pas de notification poussée en V2).
- **IA locale : abandonnée d'un commun accord.** L'analyse de texte intégrée (dates et mots-clés français) couvre la capture. Si un jour vous voulez des résumés rédigés, l'option serait l'API Claude — mais vos données partiraient dans le cloud, à décider ensemble.

## 5. Feuille de route

1. ✅ **V1** — semaine, veille, priorité du jour, espace partagé
2. ✅ **V2** — objectifs clé en main, rituel du matin + réveil, capture GTD + analyse du français, conseils des 6 livres, temps d'écran + Pacte
3. ✅ **V3** — navigation entre semaines, boussole, conseils visuels, Health Connect + saisies de secours, menus + liste de courses, graphiques 4 semaines

### Sommeil & sport : ce qui marchera vraiment
Health Connect ne contient **que** ce qu'une montre, un bracelet ou une appli (Samsung Health…) y écrit. Sans source, sommeil et séances resteront à zéro — c'est une limite d'Android, pas de l'application. D'où la saisie de secours dans **Réglages → Sommeil & sport** : deux heures à taper pour la nuit, un bouton `20′ / 30′ / 45′ / 60′` pour une séance. Une mesure automatique n'écrase jamais une saisie manuelle du même jour.

## 6. Pour les curieux : compiler soi-même

Ouvrir le dossier dans Android Studio (dernière version stable) et lancer « Run ». Rien d'autre à configurer.
