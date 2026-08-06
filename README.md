# Notre Semaine

Application Android pour deux personnes : planifier la semaine, préparer le lendemain en 2 minutes, suivre UNE priorité par jour et par semaine, et se soutenir mutuellement — sans surcharge, sans gamification.

**Version actuelle : V1** — planification hebdomadaire (Revue du dimanche), préparation de la veille, priorité du jour, espace partagé couple. Les mesures automatiques, le rituel du matin, les menus et l'IA locale arriveront dans les étapes suivantes (voir la feuille de route en bas).

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
| **Aujourd'hui** | LA priorité du jour en très gros, 2 tâches secondaires max, réveil et bloc de concentration prévus. Bouton « Préparer demain » en bas. |
| **Semaine** | Les 7 jours, la priorité de la semaine, l'ajout et la répartition des tâches (un tap sur un jour). Bouton « Revue du dimanche ». |
| **Nous** | Vos deux semaines côte à côte (une fois la synchronisation activée) + bouton « Envoyer un bravo 👏 ». Pas de classement, pas de compétition. |
| **Réglages** | Profil, heures des rappels, thème, synchronisation. |

Principes appliqués (issus des 6 méthodes du cahier des charges) :
- **Une seule priorité** par jour et par semaine — obligatoire, tout le reste est secondaire.
- **Limite dure : 3 tâches par jour maximum.** L'application refuse la 4ᵉ, volontairement.
- **« Qu'est-ce que j'abandonne cette semaine ? »** est une étape à part entière de la revue du dimanche.
- **Blocs de concentration** notés la veille et affichés le matin.
- **Aucune culpabilisation** : un objectif manqué s'affiche en gris neutre, jamais en rouge. Aucun badge, aucune série.
- **2 rappels maximum par jour**, aux heures que vous choisissez, désactivables.

## 3. Architecture en langage simple

- **L'application** est écrite en Kotlin (le langage officiel d'Android) avec Jetpack Compose pour l'interface. Elle tourne à 100 % sur le téléphone.
- **Les données** (tâches, priorités, plans) sont stockées **d'abord sur le téléphone** dans une petite base de données. C'est pour ça que tout marche sans réseau.
- **La synchronisation** (facultative) passe par **Supabase**, un service hébergé en Europe : chacun a son compte, vous reliez les deux comptes avec un code à 6 caractères, et seuls vous deux pouvez lire vos données (règles de sécurité vérifiées par le serveur, pas seulement par l'application).
- **Les rappels** sont des notifications locales — rien ne part sur internet pour vous les envoyer.

## 4. Honnêteté sur la suite (à lire avant les prochaines étapes)

- **Temps d'écran (S23 + Honor) : faisable.** L'API Android `UsageStatsManager` fonctionne sur les deux téléphones. Il faudra accorder une permission spéciale dans les réglages Android (je vous guiderai pas à pas), et sur le **Honor 400 Pro** il faudra en plus exclure l'app de l'optimisation de batterie de MagicOS, sinon la relève quotidienne sera tuée en arrière-plan.
- **Health Connect : faisable, mais attention au contenu.** Health Connect ne contient que ce qu'une montre, un bracelet ou une appli sportive y écrit. **Sans objet connecté, sommeil et fréquence cardiaque seront vides.** La saisie manuelle de secours (< 10 secondes) est prévue au cahier des charges et sera incluse. Les pas peuvent être comptés par le téléphone seul si une appli source les écrit dans Health Connect (par exemple Samsung Health sur le S23).
- **IA locale : faisable avec des limites.** Sur le S23 (8 Go de RAM, le maillon faible), un modèle Gemma 3n autour de 2 milliards de paramètres en int4 est le bon calibre via MediaPipe. Réaliste : capture en langage naturel, tri de la boîte de réception, liste de courses, résumé factuel — oui. Questions ouvertes « intelligentes » adaptées à votre semaine — qualité moyenne à ce gabarit, à tester avant de promettre.

## 5. Feuille de route (ordre du cahier des charges)

1. ✅ **V1** — semaine, veille, priorité du jour, espace partagé *(vous êtes ici : testez-la)*
2. ⬜ Mesures automatiques (temps d'écran, Health Connect + saisies de secours)
3. ⬜ Rituel du matin (séquence + minuteur + régularité)
4. ⬜ Menus de la semaine + liste de courses
5. ⬜ Capture rapide + objectifs personnels et communs
6. ⬜ IA locale (en dernier, en commençant par la capture en langage naturel)

## 6. Pour les curieux : compiler soi-même

Ouvrir le dossier dans Android Studio (dernière version stable) et lancer « Run ». Rien d'autre à configurer.
