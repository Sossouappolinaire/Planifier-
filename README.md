# Planning Sossou Constant

1. Installer l'APK (GitHub > Actions > Build APK > Artifacts).
2. Autoriser l'accès à WhatsApp (accessibilité), les alarmes exactes et la batterie.
3. Dans « Groupes et liens », coller le lien d'invitation du groupe (chat.whatsapp.com/…) puis « Ajouter » : l'appli retrouve le nom exact du groupe sur la page du lien.
4. Cocher dans « Planifier un envoi » les groupes qui reçoivent l'envoi. À l'heure prévue, l'appli ouvre le groupe directement avec son lien, écrit le message et appuie sur Envoyer.
5. Choisir : intervalle régulier (nombre d'envois + minutes) OU heures fixes (+ nombre de jours).
6. Écrire le message (court ou long), puis « Planifier ».

- « Tester maintenant » envoie tout de suite pour vérifier.
- Téléphone verrouillé : dans Réglages > « Déverrouillage automatique », enregistre ton code PIN (chiffres). À l'heure de l'envoi l'appli réveille l'écran, saisit le code, envoie, puis reverrouille (Android 9 minimum pour reverrouiller). L'empreinte digitale ne peut pas être utilisée par une appli. Si le code est refusé, la fonction se désactive toute seule pour ne pas bloquer le téléphone. Après la mise à jour, désactive puis réactive l'accès accessibilité si le déverrouillage ne se déclenche pas.
- Un groupe ajouté sans lien (ancienne version) est encore envoyé par recherche de son nom ; pour un envoi direct, le ré-ajouter avec son lien.
- Tous les fichiers sont à la racine (sauf .github) ; build.gradle les range à la compilation.

- Section 5 : saisir ton numéro WhatsApp ; après chaque planification, le récapitulatif (groupes trouvés + envois planifiés) est envoyé automatiquement sur ton WhatsApp, sans cliquer sur Envoyer (accessibilité requise).

- Écran : un accueil avec des boutons, chacun ouvre un panneau qui n'affiche que ce qu'il faut, avec un bouton « Retour à l'accueil » (le bouton Retour du téléphone aussi) : Planifier un envoi (quand, groupes cochés, message) • Alertes en attente • Historique • Envois validés • Échecs • Groupes et liens (ajout par lien, nom du groupe, choix WhatsApp / Business) • Réglages (autorisations, récapitulatif WhatsApp).

- Si WhatsApp et WhatsApp Business sont installés : choisir lequel utiliser dans le panneau « Groupes et liens » ; chaque WhatsApp a sa propre liste de groupes. Les doubles applications (Dual Messenger, Second Space) ne sont pas gérées.
