# Choix techniques — V20 (novembre 2026)

Ce document répond à une question posée en une phrase : *« avec ce que nous avons
aujourd'hui (abonnements Gemini et Claude, hébergement Hostinger), qu'est-ce qui
est le mieux pour l'application ? »* Réponse courte : **garder l'architecture
actuelle, changer trois réglages**. Le détail suit.

## 1. L'assistant : API, modèle local, Gemini ou Claude ?

### Ce que les abonnements donnent — et ne donnent pas

| Abonnement | Ce qu'il couvre | Ce dont l'application a besoin |
|---|---|---|
| Claude (claude.ai) | Les conversations dans l'application Claude | Une **clé API** (console.anthropic.com), facturée à l'usage, **indépendante** de l'abonnement |
| Gemini / Google One AI | L'application Gemini | Une **clé API** Google AI Studio (aistudio.google.com), avec un **palier gratuit** |
| Hostinger (mutualisé) | Sites PHP/Node + MySQL | Rien pour l'assistant |

Autrement dit : ni l'un ni l'autre des abonnements ne fait tourner l'assistant de
l'application. Il faut une clé API. L'application accepte les deux (la clé
commence par `sk-ant-` → Claude, sinon → Gemini).

### Coût réel pour nous

L'application envoie des prompts courts (quelques centaines de tokens) une
dizaine de fois par jour au plus. À ce rythme :

- **Gemini, palier gratuit** : 0 €. Contrepartie déjà affichée dans l'application :
  sur le palier gratuit, Google peut utiliser les données envoyées pour améliorer
  ses modèles. Pas de photo de repas ni de texte sensible si cela gêne.
- **Claude** : Haiku 4.5 (1 $/M tokens en entrée, 5 $/M en sortie) et Sonnet 5
  (3 $/M et 15 $/M). Pour notre usage, **moins de 1 € par mois**, données non
  utilisées pour l'entraînement. Demande de créditer quelques euros une fois.

### Un modèle léger en local ?

Techniquement possible (Gemma 3 1B via MediaPipe sur le S23 ; Gemini Nano n'est
pas disponible sur le S23 ni sur le Honor 400 Pro). Écarté pour l'instant :

- ~1 Go à télécharger et à garder sur chaque téléphone ;
- plusieurs secondes par réponse, batterie en plus ;
- qualité nettement en dessous pour composer des menus ou lire une photo.

Ce que le « local » apporterait vraiment — fonctionner sans réseau — existe déjà
pour le geste le plus fréquent : `CaptureParser` range une note (« plombier
mardi » → tâche datée mardi) **sans aucun appel réseau**. L'assistant n'est
qu'un plus, jamais un prérequis.

### Décision V20

1. **Deux vitesses chez Claude** (`Ai.kt`) : les tâches courtes et fréquentes
   (ranger une note, comprendre une phrase dictée, classer un rayon, proposer un
   défi) passent par **Haiku 4.5** — une seconde, cinq fois moins cher. Les
   tâches de jugement (menus, photo d'un repas, bilan de semaine) passent par
   **Sonnet 5**. Avec Gemini, un seul modèle Flash fait les deux.
2. **Reste au choix de l'utilisateur** : clé Gemini gratuite, ou clé Claude pour
   la confidentialité. Rien n'est envoyé tant que l'assistant est éteint.
3. **Pas de modèle embarqué.** À réévaluer si un téléphone avec Gemini Nano
   entre dans la famille.

## 2. L'hébergement : Supabase ou Hostinger ?

Le compte Hostinger est un **hébergement web mutualisé** (relaisconsignes.fr et
sous-domaines, ile-maurice.site) : PHP, Node.js, MySQL. Pas de VPS.

Ce que la synchronisation attend aujourd'hui de Supabase : Postgres + une API
REST automatique (PostgREST) + l'authentification par e-mail + les règles de
visibilité ligne par ligne (RLS : « je vois mes lignes et celles de mon
couple »). Reproduire cela sur Hostinger, c'est **écrire et maintenir un
serveur** (authentification, API, droits) pour remplacer un service gratuit qui
marche. Le gain serait nul, le risque réel.

**Décision : Supabase reste** (palier gratuit : largement suffisant pour une
famille). Hostinger garde deux usages possibles, non prioritaires :

- héberger une page de téléchargement de l'APK sous un sous-domaine, si l'on
  préfère une adresse « à nous » au lien GitHub ;
- plus tard, une sauvegarde chiffrée hebdomadaire des données, si l'on veut une
  copie hors Supabase.

## 3. Distribution de l'APK

Jusqu'ici, il fallait se connecter à GitHub pour récupérer l'APK dans l'onglet
Actions. Depuis la V20, chaque construction publie **aussi** l'APK à une adresse
fixe, publique, sans compte :

`https://github.com/stefj1208/Productivity-/releases/download/apk-<branche>/NotreSemaine.apk`

(par exemple `apk-claude-v20-solo-defis`). Sur le téléphone : ouvrir ce lien,
installer par-dessus la version précédente ; les réglages et les données sont
conservés (même clé de signature).

## 4. Rendre l'application évolutive

Deux mécanismes ont été ajoutés pour que la prochaine idée ne demande pas une
nouvelle version de la base ni un nouvel écran :

- **Les défis** (`data/Challenges.kt`) : un catalogue d'entrées *(titre, durée,
  règle, unité)*. Les trois règles — *constant*, *croissant*, *libre* —
  couvrent le jeu du minimalisme (jour n = n objets), les habitudes à quantité
  fixe, et les simples cases à cocher. Ajouter un défi = ajouter une entrée au
  catalogue. L'utilisateur peut aussi inventer le sien, ou demander une idée à
  l'assistant, avec le même moule.
- **Le mode** (`AppSettings.mode`) : `solo` ou `duo`. Les écrans lisent ce
  réglage plutôt que de supposer un binôme. Un futur mode « famille » (plus de
  deux profils) s'y ajoute sans casser les deux premiers : la table `profiles`
  accepte déjà n lignes.

## 5. À refaire côté Supabase

Une table a été ajoutée (`challenges`). Rejouer `supabase/schema.sql` dans
l'éditeur SQL du projet (le script est idempotent). Tant que ce n'est pas fait,
l'application fonctionne : seuls les défis ne sont pas sauvegardés en ligne — le
reste de la synchronisation n'est pas bloqué.
