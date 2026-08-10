# Activer la synchronisation entre vos deux téléphones (Supabase)

Durée : environ 15 minutes, une seule fois, depuis un ordinateur.
Tant que ce n'est pas fait, l'application fonctionne normalement — mais chacun ne voit que sa propre semaine.

## Étape 1 — Créer le projet Supabase (une seule personne le fait)

1. Allez sur <https://supabase.com> et cliquez **Start your project** ; créez un compte (gratuit).
2. Cliquez **New project** :
   - **Name** : `notre-semaine` (ou ce que vous voulez)
   - **Database password** : cliquez « Generate a password » et **notez-le quelque part** (vous n'en aurez normalement plus besoin, mais gardez-le).
   - **Region** : choisissez **Europe (Frankfurt)** — vos données restent en Europe.
3. Patientez 1 à 2 minutes pendant la création.

## Étape 2 — Installer le schéma de la base

1. Dans le menu de gauche, cliquez sur **SQL Editor**.
2. Cliquez **New query**.
3. Ouvrez le fichier `supabase/schema.sql` de ce projet, copiez **tout** son contenu, collez-le dans l'éditeur.
4. Cliquez **Run** (en bas à droite). Vous devez voir « Success. No rows returned ».

## Étape 3 — Désactiver la confirmation d'e-mail

Pour que la création de compte marche directement depuis l'application :

1. Menu de gauche : **Authentication** → **Sign In / Up** (ou « Providers » selon la version).
2. Dans **Email**, désactivez l'option **Confirm email**.
3. Enregistrez.

## Étape 4 — Récupérer les deux informations à coller dans l'application

1. Menu de gauche : **Project Settings** (roue dentée) → **API** (ou « Data API »).
2. Notez :
   - **Project URL** — une adresse du type `https://abcdefgh.supabase.co`
   - **anon public** key — une très longue suite de caractères.

Ces deux informations ne sont **pas** des secrets dangereux : la clé « anon » ne donne accès à rien sans compte, et les règles de sécurité installées à l'étape 2 garantissent que chaque donnée n'est lisible que par vous deux.

## Étape 5 — Dans l'application, sur CHAQUE téléphone

1. Onglet **Moi** (dernier onglet, en bas à droite) → tuile **☁️ Synchronisation**.
2. L'écran affiche « Étape 1 sur 3 ». Collez l'adresse du projet et la clé « anon public » → **Enregistrer la configuration**.
3. L'écran passe à « Étape 2 sur 3 ». Créez chacun **votre propre compte** : votre e-mail + un mot de passe (8 caractères minimum) → **Créer le compte**.

> Chacun crée un compte **différent**, sur son propre téléphone. Ce ne sont pas des comptes partagés.

## Étape 6 — Relier les deux comptes

L'écran affiche maintenant « Étape 3 sur 3 ».

1. Sur le **premier** téléphone : **Créer notre espace couple** → un code à 6 caractères s'affiche.
2. Sur le **second** téléphone : saisissez ce code → **Rejoindre**.
3. Touchez **Synchroniser maintenant** sur les deux téléphones.

C'est terminé quand la tuile **☁️ Synchronisation** de l'onglet **Moi** affiche « Reliée ✓ » avec votre e-mail. L'onglet **Nous** montre alors vos deux semaines côte à côte.

## En cas de problème

- « Vérifiez que la confirmation d'e-mail est désactivée » → refaites l'étape 3, puis réessayez **Se connecter**.
- **Rien n'apparaît dans l'onglet Nous** → les deux téléphones doivent avoir le **même code d'espace couple**. Comparez-le sur les deux : onglet **Moi** → **Synchronisation**.
- **Vous avez déjà installé une version précédente de l'application** → rejouez `supabase/schema.sql` en entier après chaque mise à jour qui le mentionne. Le script est fait pour être relancé sans risque : il ne détruit aucune donnée.
- « Code inconnu » → le code a 6 caractères, sans espaces ; recréez-en un au besoin sur le premier téléphone.
- La synchronisation se fait automatiquement à l'ouverture de l'application, quelques secondes après chaque modification, et environ une fois par heure en arrière-plan. Hors connexion, tout est conservé sur le téléphone et envoyé au retour du réseau.
